package com.dsh.budgetbook;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

/** Manual entry screen. The user picks income or expense by hand, then saves. */
public class AddActivity extends Activity {

    private int type = Txn.EXPENSE;
    private String category = "";
    private final Calendar date = Calendar.getInstance();

    private TextView segExpense;
    private TextView segIncome;
    private TextView dateView;
    private EditText amountInput;
    private EditText noteInput;
    private LinearLayout chipBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add);

        segExpense = (TextView) findViewById(R.id.seg_expense);
        segIncome = (TextView) findViewById(R.id.seg_income);
        dateView = (TextView) findViewById(R.id.input_date);
        amountInput = (EditText) findViewById(R.id.input_amount);
        noteInput = (EditText) findViewById(R.id.input_note);
        chipBox = (LinearLayout) findViewById(R.id.chip_box);

        findViewById(R.id.btn_cancel).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        segExpense.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyType(Txn.EXPENSE);
            }
        });
        segIncome.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                applyType(Txn.INCOME);
            }
        });
        dateView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pickDate();
            }
        });
        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                save();
            }
        });

        applyType(Txn.EXPENSE);
        updateDate();
        amountInput.requestFocus();
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void applyType(int newType) {
        type = newType;
        boolean income = (type == Txn.INCOME);

        segExpense.setBackgroundResource(income ? R.drawable.bg_seg_normal : R.drawable.bg_seg_selected);
        segExpense.setTextColor(income ? 0xFF000000 : 0xFFFFFFFF);
        segIncome.setBackgroundResource(income ? R.drawable.bg_seg_selected : R.drawable.bg_seg_normal);
        segIncome.setTextColor(income ? 0xFFFFFFFF : 0xFF000000);

        buildChips();
    }

    private void buildChips() {
        String[] cats = getResources().getStringArray(
                type == Txn.INCOME ? R.array.cats_income : R.array.cats_expense);

        boolean stillValid = false;
        for (int i = 0; i < cats.length; i++) {
            if (cats[i].equals(category)) {
                stillValid = true;
                break;
            }
        }
        if (!stillValid) {
            category = cats[0];
        }

        chipBox.removeAllViews();
        for (int i = 0; i < cats.length; i++) {
            final String name = cats[i];
            boolean selected = name.equals(category);

            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(13f);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(15f), dp(8f), dp(15f), dp(8f));
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_normal);
            chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF555555);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    category = name;
                    buildChips();
                }
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8f);
            chipBox.addView(chip, lp);
        }
    }

    private void updateDate() {
        dateView.setText(String.format(Locale.US, "%04d-%02d-%02d",
                date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH)));
    }

    private void pickDate() {
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                date.set(year, monthOfYear, dayOfMonth);
                updateDate();
            }
        }, date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void save() {
        long cents = Money.parse(amountInput.getText().toString());
        if (cents <= 0L) {
            Toast.makeText(this, R.string.err_amount, Toast.LENGTH_SHORT).show();
            amountInput.requestFocus();
            return;
        }

        int year = date.get(Calendar.YEAR);
        int month = date.get(Calendar.MONTH) + 1;
        int day = date.get(Calendar.DAY_OF_MONTH);

        Txn t = new Txn();
        t.id = System.currentTimeMillis();
        t.type = type;
        t.cents = cents;
        t.category = category;
        t.note = noteInput.getText().toString().trim();
        t.date = String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
        t.month = String.format(Locale.US, "%04d-%02d", year, month);
        t.created = System.currentTimeMillis();
        Store.add(this, t);

        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }
}
