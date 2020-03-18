package intl.who.covid19;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class LocationQueue extends DataQueue<Location> {

    private static final Type LIST_TYPE = new TypeToken<List<Location>>(){}.getType();

    public LocationQueue(Context context) {
        super(context, "locations.json");
    }

    @Override
    protected Type getListType() {
        return LIST_TYPE;
    }

    @Override
    protected void makeSendRequest(List<Location> data, Api.Listener listener) {
        final Api.LocationRequest request = new Api.LocationRequest(
                App.get(context).prefs().getString(Prefs.DEVICE_UID, null),
                App.get(context).prefs().getLong(Prefs.DEVICE_ID, 0));
        request.locations.addAll(data);
        new Api(context).sendLocations(request, listener);
    }

}
