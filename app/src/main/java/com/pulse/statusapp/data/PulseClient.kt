package com.pulse.statusapp.data

import android.content.Context
import com.pulse.statusapp.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.android.Android

object PulseClient {

    lateinit var supabase: SupabaseClient
        private set

    fun init(context: Context) {
        if (::supabase.isInitialized) return
        supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            httpEngine = Android.create()
            install(Postgrest)
            install(Auth)
            install(Realtime)
            install(Storage)
        }
    }
}
