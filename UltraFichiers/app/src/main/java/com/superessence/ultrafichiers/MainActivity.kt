package com.superessence.ultrafichiers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.superessence.ultrafichiers.ui.UltraApp
import com.superessence.ultrafichiers.ui.UltraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UltraTheme { UltraApp() } }
    }
}
