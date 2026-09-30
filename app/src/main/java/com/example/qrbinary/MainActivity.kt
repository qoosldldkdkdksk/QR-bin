package com.example.qrbinary

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.qrbinary.ui.GeneratorFragment
import com.example.qrbinary.ui.HistoryFragment
import com.example.qrbinary.ui.ScannerFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    private val scanner = ScannerFragment()
    private val generator = GeneratorFragment()
    private val history = HistoryFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        if (savedInstanceState == null) {
            show(scanner)
            nav.selectedItemId = R.id.nav_scan
        }
        nav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_scan -> show(scanner)
                R.id.nav_generate -> show(generator)
                R.id.nav_history -> show(history)
            }
            true
        }
    }

    private fun show(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
