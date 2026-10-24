package io.github.rakasans.foodapp

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
        setContent {
            FoodAppTheme {
                FoodAppNavHost(container)
            }
        }
    }
}
