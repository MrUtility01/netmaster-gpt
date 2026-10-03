package com.example.netmaster

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.netmaster.ui.NetMasterApp
import com.example.netmaster.ui.NetMasterViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale("fa", "IR")
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) {
                darkColorScheme(
                    primary = Color(0xFF4FC3F7),
                    secondary = Color(0xFF80CBC4),
                    tertiary = Color(0xFFFFB74D),
                    background = Color(0xFF0D1117),
                    surface = Color(0xFF161B22),
                    surfaceVariant = Color(0xFF21262D)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF0277BD),
                    secondary = Color(0xFF00897B),
                    tertiary = Color(0xFFEF6C00),
                    background = Color(0xFFF5F7FA),
                    surface = Color(0xFFFFFFFF),
                    surfaceVariant = Color(0xFFE8EEF5)
                )
            }
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val vm: NetMasterViewModel = viewModel()
                    NetMasterApp(vm)
                }
            }
        }
    }
}
