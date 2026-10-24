package io.github.rakasans.foodapp.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Stan sieci + powiadomienie o jej pojawieniu się (np. żeby pobrać rolki zapisane offline). */
class Connectivity(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    fun isOnline(): Boolean {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun onAvailable(callback: () -> Unit) {
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = callback()
        })
    }
}
