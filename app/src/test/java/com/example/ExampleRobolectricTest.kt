package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SnapLoad", appName)

        val developer = context.getString(R.string.developer_branding)
        assertEquals("Developer by Saad Hamdy", developer)

        val tagline = context.getString(R.string.tagline)
        assertEquals("Fast. Simple. Lightweight.", tagline)
    }
}
