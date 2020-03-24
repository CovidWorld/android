package intl.who.covid19.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.hashids.Hashids;

import intl.who.covid19.App;
import intl.who.covid19.Prefs;
import intl.who.covid19.R;

public class ProfileFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        long deviceId = App.get(view.getContext()).prefs().getLong(Prefs.DEVICE_ID, 0L);
        String id = new Hashids("COVID-19 super-secure and unguessable hashids salt", 6, "ABCDEFGHJKLMNPQRSTUVXYZ23456789").encode(deviceId);
        ((TextView) view.findViewById(R.id.textView_code)).setText(id);
    }
}
