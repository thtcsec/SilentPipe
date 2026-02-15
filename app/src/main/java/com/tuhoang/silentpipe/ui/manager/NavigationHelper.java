package com.tuhoang.silentpipe.ui.manager;

import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.content.Intent;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.appbar.MaterialToolbar;
import com.tuhoang.silentpipe.R;
import com.tuhoang.silentpipe.ui.main.SettingsActivity;

public class NavigationHelper {
    private final AppCompatActivity activity;
    private NavController navController;
    private DrawerLayout drawerLayout;

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

            drawerLayout = activity.findViewById(R.id.drawer_layout);
            NavigationView navigationView = activity.findViewById(R.id.nav_view);

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
            
            // Top level destinations (No back button)
            AppBarConfiguration appBarConfiguration = 
                    new AppBarConfiguration.Builder(
                        R.id.nav_home, 
                        R.id.nav_favorites, 
                        R.id.nav_find_sound 
                    ).build(); // Removed Settings from top-level as it is now in Drawer (or separate)
            
            NavigationUI.setupActionBarWithNavController(activity, navController, appBarConfiguration);

            // Custom Menu for Drawer (Right Side)
            activity.addMenuProvider(new androidx.core.view.MenuProvider() {
                @Override
                public void onCreateMenu(@androidx.annotation.NonNull Menu menu, @androidx.annotation.NonNull android.view.MenuInflater menuInflater) {
                    menuInflater.inflate(R.menu.drawer_menu, menu); // Re-use resource or create separate
                    // Clear and add only Hamburger
                    menu.clear();
                    MenuItem item = menu.add(0, R.id.action_open_drawer, 0, "Menu");
                    item.setIcon(android.R.drawable.ic_menu_sort_by_size); // Hamburger-like or custom
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                }

                @Override
                public boolean onMenuItemSelected(@androidx.annotation.NonNull MenuItem menuItem) {
                    if (menuItem.getItemId() == R.id.action_open_drawer) {
                        if (drawerLayout != null) {
                            drawerLayout.openDrawer(GravityCompat.END);
                        }
                        return true;
                    }
                    return false;
                }
            }, activity);

            // Handle Drawer Item Clicks
            if (navigationView != null) {
                // Set Version Footer
                try {
                    android.content.pm.PackageInfo pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    String version = pInfo.versionName;
                    android.widget.TextView tvFooter = activity.findViewById(R.id.tv_version_footer);
                    if (tvFooter != null) {
                        tvFooter.setText("Version " + version);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                navigationView.setNavigationItemSelectedListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.nav_settings) {
                        Intent intent = new Intent(activity, SettingsActivity.class);
                        activity.startActivity(intent);
                    } else if (id == R.id.nav_github) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/thtcsec/SilentPipe"));
                        activity.startActivity(browserIntent);
                    }
                    if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.END);
                    return true;
                });
            }
        }
    }

    public NavController getNavController() {
        return navController;
    }
}
