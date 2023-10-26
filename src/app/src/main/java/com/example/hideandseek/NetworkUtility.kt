package com.example.hideandseek

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.Toast

class NetworkUtils {
    companion object {
        /**
         * Check if the device is connected to the internet
         */
        fun checkForInternet(context: Context): Boolean {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

                return when {
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                    activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                    else -> false
                }
            } else {
                @Suppress("DEPRECATION") val networkInfo =
                    connectivityManager.activeNetworkInfo ?: return false
                @Suppress("DEPRECATION")
                return networkInfo.isConnected
            }
        }

        /**
         * Check connectivity and execute the provided action if connected.
         */
        fun checkConnectivityAndProceed(context: Context, action: () -> Unit) {
            if (NetworkUtils.checkForInternet(context)) {
                action.invoke()
            } else {
                Toast.makeText(context, "Make sure you are connected to the internet!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
