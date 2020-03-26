/*-
 * Copyright (c) 2020 Sygic a.s.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package intl.who.covid19.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import intl.who.covid19.BeaconService;
import intl.who.covid19.R;
import intl.who.covid19.UploadService;

public class HomeActivity extends AppCompatActivity {
	/** boolean Whether to ask the user immediately if he's coming from abroad */
	public static final String EXTRA_ASK_QUARANTINE = "intl.who.covid19.EXTRA_CHECK_QUARANTINE";

	private HomeFragment homeFragment;
	private MapFragment mapFragment;
	private ProfileFragment profileFragment;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		homeFragment = new HomeFragment();
		mapFragment = new MapFragment();
		profileFragment = new ProfileFragment();
		ViewPager viewPager = findViewById(R.id.viewPager);
		viewPager.setAdapter(new FragmentPagerAdapter(getSupportFragmentManager(), 0) {
			@Override
			public int getCount() {
				return 3;
			}
			@NonNull
			@Override
			public Fragment getItem(int position) {
				switch (position) {
					case 1: return mapFragment;
					case 2: return profileFragment;
					default: return homeFragment;
				}
			}
		});
		TabLayout tabLayout = findViewById(R.id.tabLayout);
		tabLayout.setupWithViewPager(viewPager);
		tabLayout.getTabAt(0).setIcon(R.drawable.home_home);
		tabLayout.getTabAt(1).setIcon(R.drawable.home_map);
		tabLayout.getTabAt(2).setIcon(R.drawable.home_profile);
		startService(new Intent(this, BeaconService.class));
		UploadService.start(this);
		if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_ASK_QUARANTINE, false)) {
			new ConfirmDialog(this, getString(R.string.home_checkQuarantine))
					.setButton1(getString(R.string.app_yes), R.drawable.bg_btn_red, v -> homeFragment.onButtonQuarantine())
					.setButton2(getString(R.string.app_no), R.drawable.bg_btn_green, null)
					.show();
		}
	}
}
