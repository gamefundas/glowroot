/**
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.informantproject.testkit;

import java.io.EOFException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ExecutionException;

import org.informantproject.api.Logger;
import org.informantproject.api.LoggerFactory;
import org.informantproject.core.MainEntryPoint;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
class SocketCommandProcessor implements Runnable {

    public static final String EXECUTE_APP_COMMAND = "EXECUTE_APP";
    public static final String GET_PORT_COMMAND = "GET_PORT";
    public static final String PING_COMMAND = "PING";

    private static final Logger logger = LoggerFactory.getLogger(SocketCommandProcessor.class);

    private final ObjectInputStream objectIn;
    private final ObjectOutputStream objectOut;

    SocketCommandProcessor(ObjectInputStream objectIn, ObjectOutputStream objectOut) {
        this.objectIn = objectIn;
        this.objectOut = objectOut;
    }

    public void run() {
        try {
            runInternal();
        } catch (EOFException e) {
            // socket was closed, terminate gracefully
            System.exit(0);
        } catch (Throwable e) {
            // this may not get logged if test jvm has been terminated already
            logger.error(e.getMessage(), e);
            System.exit(0);
        }
    }

    private void runInternal() throws Exception {
        while (true) {
            String commandName = (String) objectIn.readObject();
            if (commandName.equals(GET_PORT_COMMAND)) {
                // sychronizing with SocketHeartbeat
                synchronized (objectOut) {
                    objectOut.writeObject(MainEntryPoint.getPort());
                }
            } else if (commandName.equals(EXECUTE_APP_COMMAND)) {
                String appClassName = (String) objectIn.readObject();
                String threadName = (String) objectIn.readObject();
                Class<?> appClass = Class.forName(appClassName);
                String previousThreadName = Thread.currentThread().getName();
                Thread.currentThread().setName(threadName);
                try {
                    executeApp(appClass);
                } catch (Throwable t) {
                    logger.error(t.getMessage(), t);
                    throw new ExecutionException(t);
                } finally {
                    Thread.currentThread().setName(previousThreadName);
                }
                // sychronizing with SocketHeartbeat
                synchronized (objectOut) {
                    objectOut.writeObject("ok");
                }
            } else {
                throw new IllegalStateException("Unexpected command '" + commandName + "'");
            }
        }
    }

    private static void executeApp(Class<?> appClass) throws Exception {
        AppUnderTest app = (AppUnderTest) appClass.newInstance();
        app.executeApp();
    }
}
