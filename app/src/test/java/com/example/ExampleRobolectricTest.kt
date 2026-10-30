package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SheSecure", appName)
  }

  @Test
  fun `session manager starts logged out and persists session`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sessionManager = com.example.shesecure.auth.SessionManager.getInstance(context)
    sessionManager.clearSession()

    org.junit.Assert.assertFalse(sessionManager.isLoggedIn())
    org.junit.Assert.assertNull(sessionManager.getAccessToken())

    sessionManager.saveSession("test-token-123", "test-refresh-456", "user-uuid-789", "user@test.com", "Jane Doe", "+919876543210", 3600)

    org.junit.Assert.assertTrue(sessionManager.isLoggedIn())
    assertEquals("test-token-123", sessionManager.getAccessToken())
    assertEquals("Jane Doe", sessionManager.getDisplayName())
    assertEquals("user@test.com", sessionManager.getUserEmail())
    assertEquals("+919876543210", sessionManager.getUserPhone())

    sessionManager.clearSession()
    org.junit.Assert.assertFalse(sessionManager.isLoggedIn())
    assertEquals("", sessionManager.getUserPhone())
  }

  @Test
  fun `supabase config cleans trailing rest v1 from url`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val config = com.example.shesecure.data.remote.SupabaseConfig.getInstance(context)
    config.setCustomCredentials("https://example.supabase.co/rest/v1/", "test-key")
    assertEquals("https://example.supabase.co", config.url)
    assertEquals("example", config.projectRef)
    org.junit.Assert.assertTrue(config.isCustomConfigured)
    config.clearCustomCredentials()
    org.junit.Assert.assertFalse(config.isCustomConfigured)
  }

  @Test
  fun `local contact cache saves and retrieves contacts`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val cache = com.example.shesecure.data.local.LocalContactCache.getInstance(context)
    val testContact = com.example.shesecure.models.EmergencyContact("Emergency Mom", "+919876543211")
    cache.saveContacts(listOf(testContact))

    val contacts = cache.contacts
    assertEquals(1, contacts.size)
    assertEquals("Emergency Mom", contacts[0].contactName)
    assertEquals("+919876543211", contacts[0].phoneNumber)

    cache.saveContacts(emptyList())
  }

  @Test
  fun `supabase auth service detects configuration status`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val authService = com.example.shesecure.auth.SupabaseAuthService.getInstance(context)
    val configured = authService.isConfigured
    // When configured with placeholders, isConfigured returns false
    org.junit.Assert.assertNotNull(authService.configurationStatusMessage)
  }
}


