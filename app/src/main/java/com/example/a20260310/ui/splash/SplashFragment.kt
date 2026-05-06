package com.example.a20260310.ui.splash

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.a20260310.R

class SplashFragment : Fragment(R.layout.fragment_splash) {
    private val handler = Handler(Looper.getMainLooper())
    private val goNext = Runnable {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.splashFragment) {
            navController.navigate(R.id.action_splashFragment_to_homeFragment)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        handler.postDelayed(goNext, 700)
    }

    override fun onDestroyView() {
        handler.removeCallbacks(goNext)
        super.onDestroyView()
    }
}
