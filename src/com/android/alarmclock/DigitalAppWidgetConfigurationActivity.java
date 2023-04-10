package com.android.alarmclock;

import static android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID;
import static android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID;

import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.deskclock.R;

public class DigitalAppWidgetConfigurationActivity extends AppCompatActivity {
    private int mAppWidgetId = INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setResult(RESULT_CANCELED);
        setContentView(R.layout.digital_widget_configuration);

        View transparent = findViewById(R.id.preview_transparent);
        transparent.setOnClickListener(v -> onWidgetContainerClicked(false));
        View solid = findViewById(R.id.preview_solid);
        solid.setOnClickListener(v -> onWidgetContainerClicked(true));

        Intent intent = getIntent();
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mAppWidgetId = extras.getInt(EXTRA_APPWIDGET_ID, INVALID_APPWIDGET_ID);
            }
        }
    }

    private void onWidgetContainerClicked(boolean isSolid) {
        WidgetUtils.saveWidgetMode(this, mAppWidgetId, isSolid);
        AppWidgetManager wm = AppWidgetManager.getInstance(this);
        DigitalAppWidgetProvider.updateAppWidget(this, wm, mAppWidgetId);

        Intent result = new Intent();
        result.putExtra(EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}
