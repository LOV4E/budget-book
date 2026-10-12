package com.dsh.budgetbook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private final Calendar cursor = Calendar.getInstance();
    private List<Txn> shown = new ArrayList<Txn>();

    private TxAdapter adapter;
    private TextView monthText;
    private TextView budgetValue;
    private TextView spentText;
    private TextView leftText;
    private TextView incomeValue;
    private TextView expenseValue;
    private TextView countText;
    private View emptyBox;
    private ProgressBar bar;
    private ListView list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        monthText = (TextView) findViewById(R.id.month_text);
        budgetValue = (TextView) findViewById(R.id.budget_value);
        spentText = (TextView) findViewById(R.id.spent_text);
        leftText = (TextView) findViewById(R.id.left_text);
        incomeValue = (TextView) findViewById(R.id.income_value);
        expenseValue = (TextView) findViewById(R.id.expense_value);
        countText = (TextView) findViewById(R.id.count_text);
        emptyBox = findViewById(R.id.empty_box);
        bar = (ProgressBar) findViewById(R.id.budget_bar);
        list = (ListView) findViewById(R.id.txn_list);

        adapter = new TxAdapter();
        list.setAdapter(adapter);

        findViewById(R.id.prev_month).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                shift(-1);
            }
        });
        findViewById(R.id.next_month).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                shift(1);
            }
        });
        View.OnClickListener openBudget = new View.OnClickListener() {
            public void onClick(View v) {
                askBudget();
            }
        };
        findViewById(R.id.set_budget).setOnClickListener(openBudget);
        budgetValue.setOnClickListener(openBudget);

        findViewById(R.id.add_button).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AddActivity.class));
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < shown.size()) {
                    confirmDelete(shown.get(position));
                }
                return true;
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void shift(int months) {
        cursor.add(Calendar.MONTH, months);
        refresh();
    }

    private String monthKey() {
        return String.format(Locale.US, "%04d-%02d",
                cursor.get(Calendar.YEAR), cursor.get(Calendar.MONTH) + 1);
    }

    private void refresh() {
        final int year = cursor.get(Calendar.YEAR);
        final int month = cursor.get(Calendar.MONTH) + 1;
        final String key = monthKey();

        monthText.setText(getString(R.string.month_fmt, year, month));

        shown = Store.byMonth(this, key);

        long income = 0L;
        long expense = 0L;
        for (int i = 0; i < shown.size(); i++) {
            Txn t = shown.get(i);
            if (t.isIncome()) {
                income += t.cents;
            } else {
                expense += t.cents;
            }
        }

        incomeValue.setText(Money.YUAN + Money.fmt(income));
        expenseValue.setText(Money.YUAN + Money.fmt(expense));
        countText.setText(getString(R.string.count_fmt, shown.size()));

        long budget = Store.budget(this, key);
        if (budget < 0L) {
            budgetValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
            budgetValue.setText(R.string.tap_to_set);
            spentText.setText(getString(R.string.spent_fmt, Money.fmt(expense)));
            leftText.setText("");
            bar.setProgress(0);
        } else {
            budgetValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f);
            budgetValue.setText(Money.YUAN + Money.fmt(budget));
            spentText.setText(getString(R.string.spent_fmt, Money.fmt(expense)));

            long remain = budget - expense;
            if (remain >= 0L) {
                leftText.setText(getString(R.string.remain_fmt, Money.fmt(remain)));
            } else {
                leftText.setText(getString(R.string.over_fmt, Money.fmt(-remain)));
            }

            long permille = 0L;
            if (budget > 0L) {
                permille = expense * 1000L / budget;
            } else if (expense > 0L) {
                permille = 1000L;
            }
            if (permille > 1000L) {
                permille = 1000L;
            }
            bar.setProgress((int) permille);
        }

        adapter.notifyDataSetChanged();
        boolean empty = shown.isEmpty();
        emptyBox.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void askBudget() {
        final String key = monthKey();
        final int year = cursor.get(Calendar.YEAR);
        final int month = cursor.get(Calendar.MONTH) + 1;

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_budget, null);
        TextView title = (TextView) content.findViewById(R.id.budget_dialog_title);
        final EditText input = (EditText) content.findViewById(R.id.budget_input);
        title.setText(getString(R.string.budget_dialog_title_fmt, year, month));

        long current = Store.budget(this, key);
        if (current >= 0L) {
            input.setText(Money.fmt(current).replace(",", ""));
            input.setSelection(input.getText().length());
        }

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.settings_title)
                .setView(content)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String raw = input.getText().toString().trim();
                        if (raw.length() == 0) {
                            Store.setBudget(MainActivity.this, key, Store.NO_BUDGET);
                            dialog.dismiss();
                            refresh();
                            return;
                        }
                        long cents = Money.parse(raw);
                        if (cents <= 0L) {
                            Toast.makeText(MainActivity.this, R.string.err_budget, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Store.setBudget(MainActivity.this, key, cents);
                        dialog.dismiss();
                        refresh();
                    }
                });
            }
        });
        dialog.show();
    }

    private void confirmDelete(final Txn txn) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) {
                        Store.remove(MainActivity.this, txn.id);
                        refresh();
                    }
                })
                .show();
    }

    private class TxAdapter extends BaseAdapter {

        public int getCount() {
            return shown.size();
        }

        public Object getItem(int position) {
            return shown.get(position);
        }

        public long getItemId(int position) {
            return shown.get(position).id;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_txn, parent, false);
            }
            Txn t = shown.get(position);

            TextView category = (TextView) row.findViewById(R.id.item_category);
            TextView sub = (TextView) row.findViewById(R.id.item_sub);
            TextView amount = (TextView) row.findViewById(R.id.item_amount);

            String cat = t.category;
            category.setText(cat == null || cat.length() == 0 ? getString(R.string.uncategorized) : cat);

            StringBuilder sb = new StringBuilder();
            if (t.note != null && t.note.length() > 0) {
                sb.append(t.note).append("   ");
            }
            sb.append(t.date);
            sub.setText(sb.toString());

            amount.setText(Money.signed(t.cents, t.isIncome()));
            amount.setTextColor(t.isIncome() ? 0xFF000000 : 0xFF555555);
            amount.setTypeface(null, t.isIncome() ? Typeface.BOLD : Typeface.NORMAL);

            return row;
        }
    }
}
