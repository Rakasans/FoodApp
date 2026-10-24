package io.github.rakasans.foodapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.rakasans.foodapp.ui.navigation.FoodAppNavHost
import io.github.rakasans.foodapp.ui.theme.FoodAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FoodApp).container
        if (savedInstanceState == null) container.shareInbox.receive(intent)
        setContent {
            FoodAppTheme {
                FoodAppNavHost(container)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        (application as FoodApp).container.shareInbox.receive(intent)
    }
}
