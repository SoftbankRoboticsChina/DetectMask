package cn.softbankrobotics.detectmask;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.aldebaran.qi.Future;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.QiSDK;
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks;
import com.aldebaran.qi.sdk.builder.AnimateBuilder;
import com.aldebaran.qi.sdk.builder.AnimationBuilder;
import com.aldebaran.qi.sdk.builder.HolderBuilder;
import com.aldebaran.qi.sdk.builder.SayBuilder;
import com.aldebaran.qi.sdk.builder.TakePictureBuilder;
import com.aldebaran.qi.sdk.object.actuation.Animate;
import com.aldebaran.qi.sdk.object.actuation.Animation;
import com.aldebaran.qi.sdk.object.camera.TakePicture;
import com.aldebaran.qi.sdk.object.conversation.BodyLanguageOption;
import com.aldebaran.qi.sdk.object.holder.AutonomousAbilitiesType;
import com.aldebaran.qi.sdk.object.holder.Holder;
import com.aldebaran.qi.sdk.object.image.EncodedImage;
import com.aldebaran.qi.sdk.object.image.EncodedImageHandle;
import com.aliyun.facebody20191230.Client;
import com.aliyun.facebody20191230.models.DetectMaskAdvanceRequest;
import com.aliyun.facebody20191230.models.DetectMaskResponse;
import com.aliyun.tearpc.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class MainActivity extends AppCompatActivity implements RobotLifecycleCallbacks {
    private final String TAG = "MainActivity";
    private QiContext mQiContext;
    private ImageView mImageView;
    private SayBuilder mSayBuilder;
    private boolean isStart;
    private Holder mHolder;
    private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(5);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        QiSDK.register(this, this);
        mImageView = findViewById(R.id.image_view);
        Button mTakePicture = findViewById(R.id.take_picture);
        mTakePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (getResources().getText(R.string.start).equals(mTakePicture.getText())) {
                    isStart = true;
                    takePic();
                    mTakePicture.setText(R.string.stop);
                } else {
                    isStart = false;
                    mTakePicture.setText(R.string.start);
                }
            }
        });
    }

    private void takePic() {
        Future<TakePicture> takePictureFuture = TakePictureBuilder.with(mQiContext).buildAsync();

        takePictureFuture.andThenCompose(takePicture -> {
            return takePicture.async().run();
        }).andThenConsume(result -> {
            EncodedImageHandle encodedImageHandle = result.getImage();
            EncodedImage encodedImage = encodedImageHandle.getValue();

            ByteBuffer buffer = encodedImage.getData();
            buffer.rewind();
            final int pictureBufferSize = buffer.remaining();
            final byte[] pictureArray = new byte[pictureBufferSize];
            buffer.get(pictureArray);

            //获得照片
            final Bitmap pictureBitmap = BitmapFactory.decodeByteArray(pictureArray, 0, pictureBufferSize);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //show picture
                    mImageView.setImageBitmap(pictureBitmap);
                }
            });
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Config config = new Config();
                    config.accessKeyId = "######";
                    config.accessKeySecret = "######";
                    config.type = "access_key";
                    config.regionId = "cn-shanghai";
                    config.endpoint = "facebody.cn-shanghai.aliyuncs.com";
                    Client client = null;
                    try {
                        client = new Client(config);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    try {
                        DetectMaskAdvanceRequest req = new DetectMaskAdvanceRequest();
                        req.imageURLObject = Bitmap2InputStream(pictureBitmap);
                        RuntimeOptions runtimeOptions = new RuntimeOptions();
                        runtimeOptions.connectTimeout = 10000;
                        DetectMaskResponse rep = client.detectMaskAdvance(req, runtimeOptions);
                        Log.d(TAG, "Detect mask result:" + rep.data.mask);
                        sayDetectResult(rep.data.mask);
                    } catch (Exception e) {
                        Log.d(TAG, "Detect mask error:" + e.toString());
                        e.printStackTrace();
                    }
                }
            });
        });
    }

    private InputStream Bitmap2InputStream(Bitmap bm) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    @Override
    public void onRobotFocusGained(QiContext qiContext) {
        this.mQiContext = qiContext;
        Animation myAnimation = AnimationBuilder.with(qiContext)
                .withResources(R.raw.animation_init)
                .build();

        Animate animate = AnimateBuilder.with(qiContext)
                .withAnimation(myAnimation)
                .build();

        Future<Void> animateFuture = animate.async().run();
        animateFuture.thenConsume(future -> {
            if (future.hasError()) {
                Log.d(TAG, "Failed to initialize posture");
            }
            holdAbilities();
        });
    }

    private void sayDetectResult(Integer result) {
        String text = getString(R.string.no_face);
        switch (result) {
            case 0:
                text = getString(R.string.no_face);
                break;
            case 1:
                text = getString(R.string.no_mask);
                break;
            case 2:
                text = getString(R.string.correctly_mask);
                break;
            case 3:
                text = getString(R.string.wrong_mask);
                break;
        }
        Log.d(TAG, "say detect result:" + text);
        // Build the action.
        if (mSayBuilder == null) {
            mSayBuilder = SayBuilder.with(mQiContext);
        }
        Future<Void> sayFuture = mSayBuilder.withText(text)
                .withBodyLanguageOption(BodyLanguageOption.DISABLED)
                .build().async().run();

        sayFuture.thenConsume(future -> {
            if (future.hasError()) {
                Log.e(TAG, "say detect result error:" + future.getError());
            }
            if (isStart) {
                takePic();
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mImageView.setImageResource(R.drawable.ic_camera);
                    }
                });
            }
        });
    }

    //Hold autonomous abilities
    private Future<Void> holdAbilities() {
        // Build the holder for the abilities.
        if (mHolder == null) {
            mHolder = HolderBuilder.with(mQiContext)
                    .withAutonomousAbilities(
                            AutonomousAbilitiesType.BACKGROUND_MOVEMENT,
                            AutonomousAbilitiesType.BASIC_AWARENESS
                    )
                    .build();
        }
        // Hold the abilities asynchronously.
        return mHolder.async().hold();
    }

    @Override
    public void onRobotFocusLost() {
        Log.d(TAG, "Robot Focus Lost");
    }

    @Override
    public void onRobotFocusRefused(String reason) {
        Log.d(TAG, "onRobotFocusRefused: " + reason);
    }

    @Override
    protected void onDestroy() {
        // Unregister the RobotLifecycleCallbacks for this Activity.
        QiSDK.unregister(this, this);
        super.onDestroy();
    }
}
