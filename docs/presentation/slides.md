# Embabel in five minutes: slides

Twelve slides, dark background, one idea each. Everything below a `---` line is the whole text of that slide. Code slides are screenshots with syntax highlighting, never pasted text. Render the two diagrams as images before presenting.

---

## 1

**Embabel**

Agents the planner runs

---

## 2

**Typed methods in. Plan out.**

`@Action` · `@Condition` · `@AchievesGoal`

---

## 3

Image: boxes for the actions, a GOAP planner above them picking the arrows, the goal type on the right.

```
 @Action  @Action  @Action        goal
 [ A ] -> [ B ] -> [ C ] -> ... -> [ SpendingReport ]
    \______ planner picks the path ______/
```

---

## 4

**Conditions settle in `post`**

Two actions · one output type · opposite `pre`

---

## 5

**Spending coach**

CSVs in · 7 files out · 3 model calls at most

---

## 6

Image: the three forks from the POC 05 plan diagram.

```
sniffLayouts ─┬─ allLayoutsKnown ─▶ useKnownLayouts ─────────┐
              └─ hasUnknownLayout ▶ inferLayouts (LLM) ──────┴▶ parseStatements ▶ applyRules
applyRules ───┬─ allCategorised ──▶ acceptRuledLedger ───────┐
              └─ hasUncategorised ▶ categoriseRemainder (LLM)┴▶ analyse
analyse ──────┬─ noCoaching ──────▶ skipCoaching ────────────┐
              └─ wantsCoaching ───▶ coach (LLM) ─────────────┴▶ writeReport
```

---

## 7

Screenshot: `SpendingCoachAgent.java` lines 118 to 151

`sniffLayouts` · two `@Condition`s · `useKnownLayouts`

---

## 8

Screenshot: `SpendingCoachAgent.java` lines 272 to 320

distinct merchants · `parallelMap` · `PiiGuardRail`

---

## 9

Recording, 35 seconds

---

## 10

**9 actions · 0 model calls**

same bank next month: still 0

---

## 11

**Trade-offs**

types are the contract · deterministic by default · tests need no key

missed `post` = STUCK · termination hides the reason · 100 s per flow test class

---

## 12

**Hand-written graph or planned route**

Spring AI · LangChain4j · LangGraph · Embabel
