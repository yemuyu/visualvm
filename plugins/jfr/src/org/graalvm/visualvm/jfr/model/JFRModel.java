/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.visualvm.jfr.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.graalvm.visualvm.core.model.Model;
import org.openide.util.Lookup;

/**
 *
 * @author Jiri Sedlacek
 */
public abstract class JFRModel extends Model {
    
    public abstract void visitEvents(JFREventVisitor... visitors);
    
    public abstract void visitEventTypes(JFREventTypeVisitor... visitors);
    
    
    private long jvmStartTime = -1;
    private long jvmShutdownTime = -1;
    private String jvmShutdownReason;
    
    private long firstEventTime = -1;
    private long lastEventTime;
    private long eventsCount = 0;
    
    private String jvmFlags;
    private String jvmArgs;
    private String javaArgs;
    private Properties sysProps;
    
    
    private Map<String, Boolean> checkedEvents;
    
    public boolean containsEvent(Class<? extends JFREventChecker> eventCheckerClass) {
        Boolean contains = checkedEvents == null ? null : checkedEvents.get(eventCheckerClass.getName());
        return Boolean.TRUE.equals(contains);
    }
    
    
    public long getJvmStartTime() {
        return jvmStartTime;
    }

    public long getJvmShutdownTime() {
        return jvmShutdownTime;
    }

    public String getJvmShutdownReason() {
        return jvmShutdownReason;
    }
    
    
    public long getFirstEventTime() {
        return firstEventTime;
    }

    public long getLastEventTime() {
        return lastEventTime;
    }
    
    public long getEventsCount() {
        return eventsCount;
    }
    

    public Properties getSystemProperties() {
        return sysProps;
    }

    public String getJvmFlags() {
        return jvmFlags;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public String getJavaCommand() {
        return javaArgs;
    }
    
    
    public String getVmVersion() {
        return findByName("java.vm.version"); //NOI18N
    }
    
    public String getJavaHome() {
        return findByName("java.home"); //NOI18N
    }
    
    public String getVmInfo() {
        return findByName("java.vm.info"); //NOI18N
    }
    
    public String getVmName() {
        return findByName("java.vm.name"); //NOI18N
    }
    
    private String findByName(String key) {
        Properties p = getSystemProperties();
        return p == null ? null : p.getProperty(key);
    }
    
    
    protected final void initialize() {
        sysProps = new Properties();

        visitEvents(new JFREventVisitor() {
            private List<? extends JFREventChecker> checkers;
            @Override
            public void init() {
                checkedEvents = new HashMap();
                checkers = new ArrayList(Lookup.getDefault().lookupAll(JFREventChecker.class));
            }
            @Override
            public boolean visit(String typeName, JFREvent event) {
                eventsCount++;
                
                if (!checkers.isEmpty()) {
                    Iterator<? extends JFREventChecker> checkersI = checkers.iterator();
                    while (checkersI.hasNext()) {
                        JFREventChecker checker = checkersI.next();
                        if (checker.checksEventType(typeName)) {
                            checkersI.remove();
                            checkedEvents.put(checker.getClass().getName(), Boolean.TRUE);
                        }
                    }
                }

                try {
                    long eventTime = event.getInstant("eventTime").toEpochMilli(); // NOI18N
                    if (firstEventTime == -1) {
                        firstEventTime = eventTime;
                        lastEventTime = eventTime;
                    } else {
                        firstEventTime = Math.min(firstEventTime, eventTime);
                        lastEventTime = Math.max(lastEventTime, eventTime);
                    }

                    if (TYPE_JVM_INFORMATION.equals(typeName)) {
                        jvmStartTime = event.getInstant("jvmStartTime").toEpochMilli(); // NOI18N

                        jvmFlags = event.getString("jvmFlags"); // NOI18N
                        jvmArgs = event.getString("jvmArguments"); // NOI18N
                        javaArgs = event.getString("javaArguments"); // NOI18N
                    } else if (TYPE_SYSTEM_PROPERTY.equals(typeName)) {
                        sysProps.put(event.getString("key"), event.getString("value")); // NOI18N
                    } else if (TYPE_SHUTDOWN.equals(typeName)) {
                        jvmShutdownTime = event.getInstant("eventTime").toEpochMilli(); // NOI18N
                        jvmShutdownReason = event.getString("reason"); // NOI18N
                    }
                } catch (JFRPropertyNotAvailableException e) {
//                    LOGGER.log(Level.SEVERE, "JFR11Model initialization failed", e); // NOI18N
                }

                return false;
            }
        });
    }
    
    private static final String TYPE_JVM_INFORMATION = "jdk.JVMInformation"; // NOI18N
    private static final String TYPE_SYSTEM_PROPERTY = "jdk.InitialSystemProperty"; // NOI18N
    private static final String TYPE_SHUTDOWN = "jdk.Shutdown"; // NOI18N
    
}
