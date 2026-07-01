package org.renpy.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WindowSystemTest {

    @Test
    fun testDesktopWindowManagerBroadcasts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val latch = CountDownLatch(1)
        var receivedId: String? = null
        var receivedState: String? = null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == DesktopWindowManager.ACTION_WINDOW_STATE_CHANGED) {
                    receivedId = intent.getStringExtra(DesktopWindowManager.EXTRA_ACTIVITY_ID)
                    receivedState = intent.getStringExtra(DesktopWindowManager.EXTRA_STATE)
                    latch.countDown()
                }
            }
        }

        val filter = IntentFilter(DesktopWindowManager.ACTION_WINDOW_STATE_CHANGED)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        try {
            DesktopWindowManager.notifyStateChanged(context, "test_id", "Test App", "RUNNING")
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals("test_id", receivedId)
            assertEquals("RUNNING", receivedState)
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    @Test
    fun testMinimizeAndRestoreCommands() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(BaseActivity.KEY_WINDOW_MODE, "windowed").apply()

        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isWindowMinimizedState)
            }

            DesktopWindowManager.sendCommand(context, TestActivity::class.java.name, "MINIMIZE")
            
            SystemClock.sleep(500)

            scenario.onActivity { activity ->
                assertTrue(activity.isWindowMinimizedState)
            }

            DesktopWindowManager.sendCommand(context, TestActivity::class.java.name, "RESTORE")
            
            SystemClock.sleep(500)

            scenario.onActivity { activity ->
                assertFalse(activity.isWindowMinimizedState)
                activity.finish()
            }
        }
    }

    @Test
    fun testDragBoundaryConstraints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(BaseActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(BaseActivity.KEY_WINDOW_MODE, "windowed").apply()

        ActivityScenario.launch(TestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val headerView = activity.findViewById<View>(R.id.headerLayout)
                assertNotNull("Header layout must be present", headerView)

                val displayMetrics = activity.resources.displayMetrics
                val halfWidth = displayMetrics.widthPixels / 2
                val halfHeight = displayMetrics.heightPixels / 2

                val downTime = SystemClock.uptimeMillis()
                val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                headerView.dispatchTouchEvent(downEvent)
                downEvent.recycle()

                val moveTime = SystemClock.uptimeMillis()
                val moveEvent = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, 10000f, 10000f, 0)
                headerView.dispatchTouchEvent(moveEvent)
                moveEvent.recycle()

                val w = activity.window
                assertNotNull(w)
                val params = w.attributes

                assertEquals("x should be clamped to half screen width", halfWidth, params.x)
                assertEquals("y should be clamped to half screen height", halfHeight, params.y)

                val downEvent2 = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
                headerView.dispatchTouchEvent(downEvent2)
                downEvent2.recycle()

                val moveEvent2 = MotionEvent.obtain(downTime, moveTime, MotionEvent.ACTION_MOVE, -20000f, -20000f, 0)
                headerView.dispatchTouchEvent(moveEvent2)
                moveEvent2.recycle()

                val params2 = w.attributes
                assertEquals("x should be clamped to -half screen width", -halfWidth, params2.x)

                val windowHeight = if (params2.height > 0) params2.height else displayMetrics.heightPixels
                val expectedMinY = (windowHeight / 2) - halfHeight
                assertEquals("y should be clamped to minY to keep title bar on-screen", expectedMinY, params2.y)
                
                activity.finish()
            }
        }
    }
}
