package com.jonathaxs.tinystock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jonathaxs.tinystock.ui.RootScreen
import com.jonathaxs.tinystock.ui.theme.TinyStockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TinyStockTheme {
                RootScreen()
            }
        }
    }
}
