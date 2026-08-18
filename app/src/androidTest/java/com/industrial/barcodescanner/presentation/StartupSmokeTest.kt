package com.industrial.barcodescanner.presentation

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start regression test for the application entry point.
 *
 * This deliberately does not interact with the scanner so it exercises the
 * same splash, Application, Hilt, DataStore, Room, and first Compose frame
 * path seen when a user launches the app from the launcher.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupSmokeTest {

    @Test
    fun coldLaunch_reachesAndRemainsResumed() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(
                "MainActivity did not reach RESUMED after a cold launch",
                scenario.state == Lifecycle.State.RESUMED
            )
            scenario.onActivity { activity ->
                assertFalse("MainActivity finished immediately after launch", activity.isFinishing)
                assertFalse("MainActivity was destroyed immediately after launch", activity.isDestroyed)
            }
        }
    }
}
