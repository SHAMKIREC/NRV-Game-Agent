package io.nrv.gameagent.poker;

import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;
import java.util.Locale;

public final class PokerLabActivity extends AppCompatActivity {
    private EditText heroInput;
    private EditText boardInput;
    private EditText opponentsInput;
    private TextView resultView;

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
