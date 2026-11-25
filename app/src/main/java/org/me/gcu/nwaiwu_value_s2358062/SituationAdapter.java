package org.me.gcu.nwaiwu_value_s2358062;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

/**
 * RecyclerView Adapter binding Situation data items to card views in SituationListFragment.
 */
public class SituationAdapter extends RecyclerView.Adapter<SituationAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(Situation situation);
    }

    private ArrayList<Situation> situations;
    private final OnItemClickListener listener;

    public SituationAdapter(ArrayList<Situation> situations, OnItemClickListener listener) {
        this.situations = situations != null ? situations : new ArrayList<>();
        this.listener = listener;
    }

    public void updateList(ArrayList<Situation> newList) {
        this.situations = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_situation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Situation situation = situations.get(position);

        // 1. Title & Comment Text
        holder.situationTypeText.setText(formatReadableText(situation.getSituationType()));

        String comment = situation.getComment();
        holder.commentText.setText((comment != null && !comment.trim().isEmpty())
                ? comment.trim().replaceAll("\\s+", " ")
                : "Tap to view full traffic details.");

        // 2. Icon & Duration Badge Setup
        boolean isRoadwork = situation.getFeedCategory() != null
                && situation.getFeedCategory().toLowerCase(Locale.UK).contains("roadwork");

        if (isRoadwork) {
            holder.typeIcon.setImageResource(android.R.drawable.ic_menu_manage);
            holder.typeIcon.setColorFilter(Color.parseColor("#EF6C00"));

            long days = situation.getDurationDays();
            if (days >= 0) {
                holder.durationBadge.setVisibility(View.VISIBLE);
                holder.durationBadge.setText(formatFriendlyDuration(days));

                GradientDrawable badgeBg = new GradientDrawable();
                badgeBg.setCornerRadius(30f);

                // Color badge based on duration (Green = Short <=7d, Amber = Medium <=30d, Red = Long >30d)
                if (days <= 7) {
                    badgeBg.setColor(Color.parseColor("#2E7D32"));
                } else if (days <= 30) {
                    badgeBg.setColor(Color.parseColor("#EF6C00"));
                } else {
                    badgeBg.setColor(Color.parseColor("#C62828"));
                }
                holder.durationBadge.setBackground(badgeBg);
            } else {
                holder.durationBadge.setVisibility(View.GONE);
            }
        } else {
            holder.typeIcon.setImageResource(android.R.drawable.ic_dialog_alert);
            holder.typeIcon.setColorFilter(Color.parseColor("#C62828"));
            holder.durationBadge.setVisibility(View.GONE);
        }

        // 3. Item Click Listener
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(situation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return situations.size();
    }

    /**
     * Formats total days into human-friendly duration text (e.g. "Short • 3 days", "Medium • 2 wks").
     */
    private String formatFriendlyDuration(long days) {
        if (days == 0) {
            return "Short • Today";
        } else if (days == 1) {
            return "Short • 1 day";
        } else if (days <= 7) {
            return "Short • " + days + " days";
        } else if (days < 30) {
            long weeks = Math.max(1, Math.round(days / 7.0));
            return "Medium • " + weeks + (weeks == 1 ? " wk" : " wks");
        } else if (days < 365) {
            long months = Math.max(1, Math.round(days / 30.0));
            return "Long • " + months + (months == 1 ? " mo" : " mos");
        } else {
            return "Long-term";
        }
    }

    /**
     * Formats camelCase strings into spaced Title Case words.
     */
    private String formatReadableText(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "Traffic Situation";
        String spaced = raw.replace("_", " ")
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .trim();
        String[] words = spaced.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase(Locale.UK))
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView typeIcon;
        TextView situationTypeText;
        TextView durationBadge;
        TextView commentText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            typeIcon = itemView.findViewById(R.id.typeIcon);
            situationTypeText = itemView.findViewById(R.id.situationTypeText);
            durationBadge = itemView.findViewById(R.id.durationBadge);
            commentText = itemView.findViewById(R.id.commentText);
        }
    }
}