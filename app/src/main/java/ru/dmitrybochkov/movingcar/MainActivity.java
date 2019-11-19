package ru.dmitrybochkov.movingcar;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    public static final int MAX_RADIUS = 600;

    private CarView carView;
    private SeekBar radiusSeekBar;
    private TextView radiusTextView;
    private CheckBox showDestination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        carView = findViewById(R.id.car_view);
        radiusSeekBar = findViewById(R.id.radius);
        radiusTextView = findViewById(R.id.radius_value);
        showDestination = findViewById(R.id.show_destination);

        radiusTextView.setText(String.valueOf(carView.getRadius()));
        radiusSeekBar.setProgress(Math.round(((float)carView.getRadius() / MAX_RADIUS) * 100));

        radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int r = (MAX_RADIUS / 100 * seekBar.getProgress());
                if (r == 0)
                    ++r;
                radiusTextView.setText(String.valueOf(r));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int r = (MAX_RADIUS / 100 * seekBar.getProgress());
                if (r == 0)
                    ++r;
                carView.setRadius(r);
            }
        });

        showDestination.setChecked(carView.isShowDestination());
        showDestination.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                carView.setShowDestination(isChecked);
            }
        });
    }
}

