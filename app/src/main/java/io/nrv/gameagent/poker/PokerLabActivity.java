package io.nrv.gameagent.poker;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public final class PokerLabActivity extends AppCompatActivity {
    private EditText heroInput;
    private EditText boardInput;
    private EditText opponentsInput;
    private TextView resultView;
    private TextView visionView;

    private final PokerVisionAnalyzer visionAnalyzer = new PokerVisionAnalyzer();

    private final ActivityResultLauncher<String> screenshotPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::analyzeScreenshot);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Poker Lab");
        setContentView(buildContent());
    }

    private ScrollView buildContent() {
        int p = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("POKER LAB · TEXAS HOLD'EM");
        title.setTextSize(26);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Учебный расчёт equity. Формат карт: AS QH, стол: JS TS 4D");
        hint.setTextSize(15);
        hint.setPadding(0, dp(8), 0, dp(20));
        root.addView(hint);

        Button screenshot = new Button(this);
        screenshot.setText("ЗАГРУЗИТЬ СКРИНШОТ СТОЛА");
        screenshot.setOnClickListener(v -> screenshotPicker.launch("image/*"));
        root.addView(screenshot, fullWidth(0));

        visionView = new TextView(this);
        visionView.setText("Vision: скриншот ещё не выбран.");
        visionView.setTextSize(15);
        visionView.setPadding(0, dp(12), 0, dp(20));
        root.addView(visionView);

        heroInput = field("Твои 2 карты, например: AS QS");
        boardInput = field("Общие карты 0–5, например: JS TS 4D");
        opponentsInput = field("Количество соперников, например: 2");
        opponentsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        root.addView(heroInput);
        root.addView(boardInput);
        root.addView(opponentsInput);

        Button calculate = new Button(this);
        calculate.setText("РАССЧИТАТЬ 20 000 СИМУЛЯЦИЙ");
        calculate.setOnClickListener(v -> calculate());
        root.addView(calculate, fullWidth(dp(12)));

        resultView = new TextView(this);
        resultView.setText("Введите карты и нажмите расчёт.");
        resultView.setTextSize(20);
        resultView.setPadding(0, dp(24), 0, dp(24));
        root.addView(resultView);

        return scroll;
    }

    private void analyzeScreenshot(Uri uri) {
        if (uri == null) return;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream);
            PokerVisionObservation observation = visionAnalyzer.analyze(bitmap);

            StringBuilder regions = new StringBuilder();
            int shown = Math.min(8, observation.regions().size());
            for (int i = 0; i < shown; i++) {
                PokerVisionObservation.Region region = observation.regions().get(i);
                regions.append(String.format(Locale.US,
                        "\n#%d x=%.2f y=%.2f w=%.2f h=%.2f",
                        i + 1,
                        region.centerX(),
                        region.centerY(),
                        region.width(),
                        region.height()));
            }

            visionView.setText(String.format(Locale.US,
                    "Vision diagnostics\nКандидатов-карт: %d\nВероятно твои карты: %d/2\nВероятно board: %d/5\nСтадия: %s\nУверенность: %.0f%%%s\n\nСледующий этап: распознавание ранга и масти после калибровочного скриншота.",
                    observation.cardCandidates(),
                    observation.likelyHeroCards(),
                    observation.likelyBoardCards(),
                    observation.stage(),
                    observation.confidence() * 100.0,
                    regions));
        } catch (Exception error) {
            visionView.setText("Не удалось прочитать скриншот: " + error.getMessage());
        }
    }

    private EditText field(String hint) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setTextSize(17);
        input.setSingleLine(true);
        input.setLayoutParams(fullWidth(dp(8)));
        return input;
    }

    private void calculate() {
        try {
            List<Card> hero = EquityCalculator.parseCards(heroInput.getText().toString());
            List<Card> board = EquityCalculator.parseCards(boardInput.getText().toString());
            int opponents = Integer.parseInt(opponentsInput.getText().toString().trim());
            EquityCalculator.Result result = new EquityCalculator().calculate(hero, board, opponents, 20_000);
            resultView.setText(String.format(Locale.US,
                    "Победа: %.1f%%\nНичья: %.1f%%\nПоражение: %.1f%%\n\nСимуляций: %,d",
                    result.win() * 100.0,
                    result.tie() * 100.0,
                    result.lose() * 100.0,
                    result.simulations()));
        } catch (Exception error) {
            resultView.setText("Ошибка ввода: " + error.getMessage());
        }
    }

    private LinearLayout.LayoutParams fullWidth(int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = top;
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
