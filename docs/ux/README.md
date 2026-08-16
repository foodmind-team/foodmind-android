# FoodMind Android UX

The native home screen shares the same two-mode architecture as the web app:
**Eat out & delivery** is the recommendation-first default, while **Cooking**
opens the account-recipe selection flow directly and plans against current inventory.

## UX priorities

- Keep the mode switch and “Generate recommendation” action in the first viewport.
- Keep long recommendation context on its own destination instead of expanding Home.
- Show one primary action per screen; move Kitchen utilities and low-frequency recipe input into secondary menus and sheets.
- Use group ratings, personal history, budget, distance, and dietary constraints
  as understandable recommendation context.
- Reveal one confident result at a time, with actions to share it with the group
  or generate another.
- Preserve the Proposal contract by retaining up to three ordered candidate
  types behind the lead-result presentation.
- Make the shared group card and active vote easy to reach from the Groups tab.
- Preview authorised group-visible and curated posts from Explore; do not imply
  a public follower feed.
- Keep Home, Groups, Explore, Saved, and Me in a fixed, labeled bottom navigation.
- Preserve 48dp touch targets, native scrolling, system insets, and accessible
  content descriptions.
- Use the shared dark design tokens across phone and expanded layouts.

Automatic inventory capture, public restaurant search, ordering, and payment
remain outside the MVP.

## Preview

![FoodMind Android recommendation home](today-dashboard-android.png)
