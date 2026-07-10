package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class GlassesViewModel(application: Application) : AndroidViewModel(application) {

    val bleManager = GlassesBLEManager(application)
}
