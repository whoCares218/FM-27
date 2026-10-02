package com.footymanager.simulator

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Launches [MainActivity] exactly the way the launcher does, so a crash during
 * startup fails the build instead of only showing up on a real device.
 *
 * This guards a specific, easy-to-reintroduce bug: the platform instantiates an
 * `AndroidViewModel` reflectively and only looks for a single-argument
 * `(Application)` constructor. Kotlin default arguments do not generate that
 * overload, so removing the secondary constructor makes the app die on launch.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class StartupSmokeTest {

    @Test
    fun `MainActivity launches and reaches resumed state`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup() // create -> start -> resume
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val activity = controller.get()
        assertNotNull("Activity should be created", activity)
        assertFalse("Activity should not be finishing after startup", activity.isFinishing)
        assertFalse("Activity should not be destroyed after startup", activity.isDestroyed)

        controller.pause().stop().destroy()
    }
}
