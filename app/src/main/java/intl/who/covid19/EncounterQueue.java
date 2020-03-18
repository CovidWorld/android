package intl.who.covid19;

import android.content.Context;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;

public class EncounterQueue extends DataQueue<Encounter> {

    private static final Type LIST_TYPE = new TypeToken<List<Encounter>>(){}.getType();

    public EncounterQueue(Context context) {
        super(context, "encounters.json");
    }

    @Override
    protected Type getListType() {
        return LIST_TYPE;
    }

    @Override
    protected void makeSendRequest(List<Encounter> data, Api.Listener listener) {
        final Api.ContactRequest request = new Api.ContactRequest(App.get(context).prefs().getString(Prefs.DEVICE_UID, null),
                App.get(context).prefs().getLong(Prefs.DEVICE_ID, 0));
        request.connections.addAll(data);
        new Api(context).sendContacts(request, listener);
    }

}
