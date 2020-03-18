package intl.who.covid19;

import android.content.Context;
import android.os.AsyncTask;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/*package*/ abstract class DataQueue<T> {

    public interface SendListener {
        void onSent();
    }

	private static class SaveTask<D> extends AsyncTask<Void, Void, Boolean> {
        private final File file;
        private final List<D> data;
        SaveTask(File file, List<D> data) {
            this.file = file;
            this.data = data;
        }
        @Override
        protected Boolean doInBackground(Void... params) {
            try (FileWriter fileWriter = new FileWriter(file)) {
                // FIXME: This is blocking the main thread that want to add an item
                synchronized (data) {
                    new Gson().toJson(data, fileWriter);
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    protected final Context context;
    private final String filename;
    private final List<T> data;
    private boolean autoSave = true;

    public DataQueue(Context context, String filename) {
        this.context = context;
        this.filename = filename;
        // Load from file
        List<T> data;
        try {
            data = new Gson().fromJson(new FileReader(new File(context.getFilesDir(), filename)), getListType());
        } catch (Exception e) {
            data = new ArrayList<>();
        }
        this.data = data;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public void add(T item) {
        synchronized (data) {
            data.add(item);
            if (autoSave) {
                save();
            }
        }
    }

    public void save() {
        autoSave = true;
        new SaveTask<>(new File(context.getFilesDir(), filename), data).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void send(SendListener listener) {
        final List<T> dataToSend;
        synchronized (data) {
            dataToSend = new ArrayList<>(data);
            data.clear();
            save();
        }
        if (dataToSend.isEmpty()) {
            listener.onSent();
            return;
        }
        makeSendRequest(dataToSend, (status, response) -> {
            if (status != 200) {
                synchronized (data) {
                    data.addAll(0, dataToSend);
                    save();
                }
            }
            listener.onSent();
        });
    }

    protected abstract Type getListType();
    protected abstract void makeSendRequest(List<T> data, Api.Listener listener);
}
