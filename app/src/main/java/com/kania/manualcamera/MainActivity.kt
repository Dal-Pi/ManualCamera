package com.kania.manualcamera

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupActionBarWithNavController

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //setupActionBarWithNavController(findNavController(R.id.fragmentContainer))
//        setupActionBarWithNavController(
//            (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment)
//                .navController)
    }

//    override fun onSupportNavigateUp(): Boolean {
//        val navController = (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment).navController
//        return navController.navigateUp() || super.onSupportNavigateUp()
//    }
}