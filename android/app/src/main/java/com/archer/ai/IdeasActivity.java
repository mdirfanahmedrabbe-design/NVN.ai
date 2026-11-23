package com.archer.ai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// Ideas — suggestion cards like the Muse WhatsApp UI. Tap a card → chat sends it.
public class IdeasActivity extends Activity {

    private static final String[][] IDEAS = {
            {"🌤 আজকের আবহাওয়া", "আজকের আবহাওয়া কেমন?"},
            {"💵 ডলার রেট", "ডলার রেট কত?"},
            {"🕌 নামাজের সময়", "নামাজের সময় বলো"},
            {"🔗 কানেক্ট করা সাইট", "আমার কানেক্ট করা সাইটগুলো দেখাও"},
            {"📝 পোস্ট দাও", "আমার কানেক্ট করা সাইটে একটা পোস্ট দাও"},
            {"🧾 খরচের হিসাব", "আমার খরচের হিসাব দেখাও"},
            {"⏰ রিমাইন্ডার", "একটা রিমাইন্ডার দাও"},
            {"📖 কুরআনের আয়াত", "একটা কুরআনের আয়াত শোনাও"},
            {"🏏 ক্রিকেট খবর", "ক্রিকেটের খবর বলো"},
            {"🔋 ফোনের অবস্থা", "ব্যাটারি কত আছে?"},
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.bg_main);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (16 * d));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("💡 Ideas");
        title.setTextColor(0xFFE7C873);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("একটা কার্ডে চাপ দিলেই ফারিহা কাজ শুরু করবে।");
        sub.setTextColor(0xFFA8C3B2);
        sub.setTextSize(13);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = (int) (12 * d);
        sub.setLayoutParams(subLp);
        root.addView(sub);

        for (String[] idea : IDEAS) {
            TextView card = new TextView(this);
            card.setText(idea[0] + "\n" + idea[1]);
            card.setTextColor(0xFFF3E9C8);
            card.setTextSize(15);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
            card.setGravity(Gravity.START);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * d);
            card.setLayoutParams(lp);
            final String prompt = idea[1];
            card.setOnClickListener(v -> {
                Intent i = new Intent(this, ChatActivity.class);
                i.putExtra("prefill", prompt);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(i);
                finish();
            });
            root.addView(card);
        }

        setContentView(scroll);
    }
}
