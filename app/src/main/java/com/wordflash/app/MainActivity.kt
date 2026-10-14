package com.wordflash.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wordflash.app.data.repository.SettingsRepository
import com.wordflash.app.ui.navigation.AppNavHost
import com.wordflash.app.ui.theme.ThemeMode
import com.wordflash.app.ui.theme.WordFlashTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 唯一的 Activity —— 整个应用由 Compose 承载。
 *
 * 主题模式与动态取色从 DataStore 读取，
 * 用户改设置后整个界面立即重组，无需重启。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // 初始值给 SYSTEM，避免首帧闪烁
            val themeMode by settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dynamicColor by settingsRepository.dynamicColor
                .collectAsStateWithLifecycle(initialValue = false)

            WordFlashTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavHost()
                }
            }
        }
    }
}
