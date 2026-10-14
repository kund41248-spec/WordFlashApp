package com.wordflash.app

import android.app.Application
import android.util.Log
import com.wordflash.app.data.repository.StatsRepository
import com.wordflash.app.data.seed.DictionarySeeder
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用入口。
 *
 * [HiltAndroidApp] 触发 Hilt 代码生成，创建全局依赖容器。
 * 首次启动时在后台完成词库播种与历史数据清理。
 */
@HiltAndroidApp
class WordFlashApp : Application() {

    @Inject lateinit var seeder: DictionarySeeder
    @Inject lateinit var statsRepository: StatsRepository

    /**
     * 应用级协程作用域。
     *
     * 用 SupervisorJob 保证某个子任务失败不会拖垮其它任务；
     * 绑到 Application 生命周期，不随任何页面销毁而取消。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        appScope.launch {
            // 1) 播种内置词库。已存在则跳过，因此重复调用是安全的。
            runCatching { seeder.seedIfNeeded() }
                .onFailure { Log.e(TAG, "词库播种失败", it) }

            // 2) 清理过期作答明细，防止数据库无限增长。
            runCatching { statsRepository.pruneOldRecords() }
                .onFailure { Log.e(TAG, "清理历史记录失败", it) }
        }
    }

    private companion object {
        const val TAG = "WordFlashApp"
    }
}
