package com.tuhoang.silentpipe.ui.manager;

import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.appbar.MaterialToolbar;
import com.tuhoang.silentpipe.R;

public class NavigationHelper {
    private final AppCompatActivity activity;
    private NavController navController;

    public NavigationHelper(AppCompatActivity activity) {
        this.activity = activity;
        setupNavigation();
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) activity.getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
            BottomNavigationView bottomNav = activity.findViewById(R.id.bottom_nav);
            NavigationUI.setupWithNavController(bottomNav, navController);

            // Hide Toolbar on Advanced EQ
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                boolean isAdvancedEQ = destination.getId() == R.id.navigation_advanced_eq;
                View appbar = activity.findViewById(R.id.appbar_layout);
                if (appbar != null) appbar.setVisibility(isAdvancedEQ ? View.GONE : View.VISIBLE);
                bottomNav.setVisibility(isAdvancedEQ ? View.GONE : View.VISIBLE); 
            });

            // Setup Toolbar
            MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
            activity.setSupportActionBar(toolbar);
            AppBarConfiguration appBarConfiguration = 
                    new AppBarConfiguration.Builder(R.id.nav_home, R.id.nav_favorites, R.id.nav_settings).build();
            NavigationUI.setupActionBarWithNavController(activity, navController, appBarConfiguration);
        }
    }

    public NavController getNavController() {
        return navController;
    }
}
