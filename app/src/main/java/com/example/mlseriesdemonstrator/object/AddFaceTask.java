package com.example.mlseriesdemonstrator.object;
import android.os.AsyncTask;
import android.text.Editable;

import com.example.mlseriesdemonstrator.helpers.vision.recogniser.FaceRecognitionProcessor;

public class AddFaceTask extends AsyncTask<Void, Void, Void> {
    private FaceRecognitionProcessor processor;
    private Editable input;
    private float[] tempVector;
    private byte[] imageBytes;


    public AddFaceTask(FaceRecognitionProcessor processor, Editable input, float[] tempVector, byte[] imageBytes) {
        this.processor = processor;
        this.input = input;
        this.tempVector = tempVector;
        this.imageBytes = imageBytes;

    }

    @Override
    protected Void doInBackground(Void... voids) {
        // Thực hiện hoạt động thêm khuôn mặt ở đây, ví dụ:
        processor.registerFace(input, tempVector, imageBytes);
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        // Gọi khi hoàn thành việc thêm khuôn mặt (cập nhật giao diện người dùng tại đây nếu cần)
    }
}
