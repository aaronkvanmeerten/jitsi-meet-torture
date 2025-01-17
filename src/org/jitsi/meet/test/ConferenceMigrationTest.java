/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.meet.test;

import org.jitsi.meet.test.util.*;

import org.junit.*;
import org.junit.runner.*;
import org.junit.runners.*;

import org.openqa.selenium.*;

import java.io.*;

import static org.junit.Assert.fail;

/**
 * Test for migrating conference from broken bridge to a new one.
 *
 * To run the tes configure {@link #MIGRATED_BRIDGE_PNAME} to point to the
 * bridge that has to be killed by the focus during the test. We'll be testing
 * conference migration from this bridge to a secondary one.
 *
 * Set {@link #MIGRATED_BRIDGE_REST_ENDPOINT_PNAME} to RESt endpoint of migrated
 * bridge. By default it is {@link #DEFAULT_BRIDGE_REST_ENDPOINT}.
 *
 * @author Pawel Domas
 */
@RunWith(JUnit4.class)
public class ConferenceMigrationTest
{
    /**
     * The name of system property that specifies the JID of the bridge that
     * we'll be shutting down in this test and want to have the conference to
     * be hosted on it from the very beginning.
     */
    public static final String MIGRATED_BRIDGE_PNAME
        = "jitsi-meet.migration-test.enforcedBridge";

    /**
     * The address of the REST API endpoint of migrated bridge.
     */
    public static final String MIGRATED_BRIDGE_REST_ENDPOINT_PNAME
        = "jjitsi-meet.migration-test.JvbRestEndpoint";

    /**
     * Default localhost address of migrated bridge.
     */
    public static final String DEFAULT_BRIDGE_REST_ENDPOINT
        = "http://localhost:8080";

    /**
     * Test conference migration from one bridge to another.
     * First we make sure that Jicofo allocates the conference on the to be
     * migrated bridge. Then we force shutdown that bridge and wait for Jicofo
     * to detect bridge failure. It then should move participants to the
     * secondary bridge.
     *
     * @throws IOException
     * @throws InterruptedException
     */
    @org.junit.Test
    public void testConferenceMigration()
        throws IOException,
               InterruptedException
    {
        String migratedBridge = System.getProperty(MIGRATED_BRIDGE_PNAME);
        if (migratedBridge == null || migratedBridge.isEmpty())
        {
            fail("The bridge to be migrated has not been specified!");
        }

        String jvbRESTEndpoint
            = System.getProperty(MIGRATED_BRIDGE_REST_ENDPOINT_PNAME);
        if (jvbRESTEndpoint == null || jvbRESTEndpoint.isEmpty())
        {
            jvbRESTEndpoint = DEFAULT_BRIDGE_REST_ENDPOINT;
        }

        System.err.println("Start conference migration test. " +
            "Migrated bridge: " + migratedBridge +
            ", REST endpoint: " + jvbRESTEndpoint);

        // Close any windows
        new DisposeConference().testDispose();

        WebDriver owner
            = ConferenceFixture.startOwner(
                "config.enforcedBridge=\"" + migratedBridge +"\"");

        WebDriver secondParticipant = ConferenceFixture.startSecondParticipant();

        ConferenceFixture.waitForParticipantToJoinMUC(owner, 10);
        ConferenceFixture.waitForParticipantToJoinMUC(secondParticipant, 10);

        ConferenceFixture.waitForIceCompleted(owner);
        ConferenceFixture.waitForIceCompleted(secondParticipant);

        ((JavascriptExecutor) owner)
            .executeScript(
                "window.location.hash=" +
                    "window.location.hash" +
                    ".replace(/\\&?config.enforcedBridge=\".+\"/,\"\");" +
                    "config.enforcedBridge=undefined;");

        // Graceful shutdown migrated bridge
        final String jvbEndpoint = jvbRESTEndpoint;
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    JvbUtil.shutdownBridge(jvbEndpoint, true);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }

        }).start();

        System.err.println("Wait for disconnected...");
        ConferenceFixture.waitForIceDisconnected(owner, 45);
        System.err.println("Owner - ICE disconnected!");
        ConferenceFixture.waitForIceDisconnected(secondParticipant, 45);
        System.err.println("Second peer - ICE disconnected!");

        // Wait for conference restart
        System.err.println("Wait for ICE reconnected...");
        ConferenceFixture.waitForIceCompleted(owner, 60);
        System.err.println("Owner - ICE reconnected!");
        ConferenceFixture.waitForIceCompleted(secondParticipant, 60);
        System.err.println("Second peer - ICE reconnected!");

    }

    @AfterClass
    static public void tearDown()
    {
        new DisposeConference().testDispose();
    }
}
