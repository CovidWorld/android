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
        File dataFile = new File(context.getFilesDir(), filename);
        List<T> data = null;
        try {
            data = new Gson().fromJson(new FileReader(dataFile), getListType());
        } catch (Exception e) {
            App.log("Can't load data from " + getClass().getSimpleName() + " file: " + dataFile + "; " + e);
        }
        if (data == null) {
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
