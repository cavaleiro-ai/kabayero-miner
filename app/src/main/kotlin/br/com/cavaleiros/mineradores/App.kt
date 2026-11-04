package br.com.cavaleiros.mineradores

import android.app.Application
import android.content.Context
import br.com.cavaleiros.mineradores.data.AppRepository

class App : Application() {

    val repository: AppRepository by lazy { AppRepository(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        private var instance: App? = null

        fun repository(context: Context): AppRepository =
            (context.applicationContext as App).repository
    }
}
