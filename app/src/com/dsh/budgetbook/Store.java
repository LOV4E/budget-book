package com.dsh.budgetbook;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Local persistence on top of SharedPreferences. No network, no permissions.
 * Records are kept as a JSON array; monthly budgets as a JSON object keyed by "yyyy-MM".
 */
public final class Store {

    private static final String PREF = "budget_book_v1";
    private static final String KEY_TXNS = "transactions";
    private static final String KEY_BUDGETS = "budgets";

    /** Sentinel meaning "no budget set for this month". */
    public static final long NO_BUDGET = -1L;

    private Store() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized List<Txn> all(Context c) {
        List<Txn> list = new ArrayList<Txn>();
        String raw = prefs(c).getString(KEY_TXNS, "");
        if (raw != null && raw.length() > 0) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null) {
                        list.add(Txn.fromJson(o));
                    }
                }
            } catch (Exception ignored) {
                // Corrupted payload: start from an empty list rather than crash.
            }
        }
        Collections.sort(list, new Comparator<Txn>() {
            public int compare(Txn a, Txn b) {
                if (a.created != b.created) {
                    return a.created > b.created ? -1 : 1;
                }
                if (a.id != b.id) {
                    return a.id > b.id ? -1 : 1;
                }
                return 0;
            }
        });
        return list;
    }

    public static synchronized List<Txn> byMonth(Context c, String month) {
        List<Txn> out = new ArrayList<Txn>();
        for (Txn t : all(c)) {
            if (month != null && month.equals(t.month)) {
                out.add(t);
            }
        }
        return out;
    }

    public static synchronized void add(Context c, Txn txn) {
        List<Txn> list = all(c);
        list.add(txn);
        save(c, list);
    }

    public static synchronized void remove(Context c, long id) {
        List<Txn> list = all(c);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).id == id) {
                list.remove(i);
            }
        }
        save(c, list);
    }

    private static void save(Context c, List<Txn> list) {
        JSONArray arr = new JSONArray();
        for (Txn t : list) {
            try {
                arr.put(t.toJson());
            } catch (Exception ignored) {
                // Skip a malformed record instead of losing the whole list.
            }
        }
        prefs(c).edit().putString(KEY_TXNS, arr.toString()).apply();
    }

    public static synchronized long budget(Context c, String month) {
        String raw = prefs(c).getString(KEY_BUDGETS, "");
        if (raw == null || raw.length() == 0) {
            return NO_BUDGET;
        }
        try {
            JSONObject o = new JSONObject(raw);
            if (!o.has(month)) {
                return NO_BUDGET;
            }
            return o.optLong(month, NO_BUDGET);
        } catch (Exception e) {
            return NO_BUDGET;
        }
    }

    /** Pass a negative value to clear the budget of that month. */
    public static synchronized void setBudget(Context c, String month, long cents) {
        JSONObject o = budgets(c);
        try {
            if (cents < 0L) {
                o.remove(month);
            } else {
                o.put(month, cents);
            }
        } catch (Exception ignored) {
            // Nothing sensible to do; keep the previous payload.
        }
        prefs(c).edit().putString(KEY_BUDGETS, o.toString()).apply();
    }

    private static JSONObject budgets(Context c) {
        String raw = prefs(c).getString(KEY_BUDGETS, "");
        if (raw != null && raw.length() > 0) {
            try {
                return new JSONObject(raw);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return new JSONObject();
    }
}
