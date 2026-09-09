# Local task learning

Afazeres learns category and priority from the user's saved choices. It uses two independent multinomial Naive Bayes classifiers implemented in Kotlin and runs entirely on the device. There is no pretrained dataset, server, API key, or network request.

## User flow

- A new installation leaves both optional fields unset.
- Choosing a category or priority in the editor and saving confirms that field as a human label. Choosing “No category” or “No priority” explicitly also counts as a choice; untouched defaults do not.
- When the history provides enough evidence, creating a new task can fill either or both fields automatically. Existing tasks are never reclassified in the background.
- Automatic fields are identified in task details. Selecting the same value in the editor confirms it; selecting another value corrects it. Saving unrelated changes does not confirm automatic labels.
- Category and priority learn independently. Confirming a category does not confirm the priority.

## Training data and persistence

Each stored task contributes at most one current example per confirmed field that has not been excluded by a reset. Both pending and completed tasks participate. A correction replaces the previous label; repeated saves and status changes do not duplicate examples. Ordinary deletion preserves the last confirmed word counts in an independent Room training table. “Delete and forget learning” removes that task’s contribution in the same transaction, for both individual and bulk deletion. Category names can change without breaking learning because labels use category IDs.

The classifier is reconstructed from the current Room snapshot when a task is created. Training and prediction run off the main thread, inside the repository's creation transaction. This avoids stale predictions during edits, deletes, imports, or category changes. The training table stores sorted, unordered word counts and confirmed labels, independently of the task. Original titles and descriptions are not copied into this table. Word counts still reveal vocabulary and are not encryption or anonymization.

Database migration 3 → 4 marks existing nonempty labels as confirmed because they predate automatic classification. Unset fields remain unconfirmed. JSON backup version 7 includes an independent `training` history as well as `categoryConfirmed`, `priorityConfirmed`, `categoryTrainingExcluded`, and `priorityTrainingExcluded`; versions 1–6 remain importable. Restoring a backup replaces the training history along with the task data. Automatic labels stay unconfirmed after an export/import round trip.

## Text representation and classifier

Both training and prediction use the task title, matching quick entry. Descriptions do not participate in this first version. Text processing lowercases with `Locale.ROOT`, normalizes accents, splits Unicode words, removes a small Portuguese/English stop-word list, and retains negation. Titles are bounded to 200 characters, up to 64 tokens, and at most three occurrences of each word. There is no stemming or semantic language model.

The implementation uses class frequencies, Laplace smoothing (alpha = 1), and log likelihoods, following the [Stanford text-classification reference](https://nlp.stanford.edu/IR-book/html/htmledition/naive-bayes-text-classification-1.html).

A field is left unset unless all these initial safeguards pass:

- At least eight confirmed, nonempty training examples for that field and at least two distinct labels.
- At least 60% of distinct query tokens occur in the learned vocabulary.
- At least three examples in the winning class share half or more of the known query tokens.
- The winning class has a lexical likelihood advantage of at least ln(3) over the runner-up, preventing class frequency alone from deciding.
- The normalized model score is at least 0.85 and the margin over the runner-up is at least 0.25.

These are conservative initial heuristics, not measured accuracy guarantees. The model score is not a calibrated confidence percentage and is not shown as one. Similar vocabulary with different meanings or priorities can still cause mistakes. Unknown vocabulary, little history, or only one label cause abstention. This version does not infer deadlines or add dates.

## Validation

Unit tests cover tokenization, cold start, ambiguous and unknown text, class imbalance, independent fields, manual overrides, corrections, deleted history, and exclusion of automatic labels from training. Room tests cover creation, repeated saves, status changes, restoration, provenance, and database upgrades. Backup tests preserve automatic/manual distinctions and validate legacy formats.

## Audit and reset

Settings → Local learning shows the exact eligible examples used by each model, grouped by category or priority. Existing tasks can be opened for correction; deleted tasks are represented only by aggregate counts within each category or priority group. Empty text representations, unconfirmed automatic labels, and reset examples are excluded from both the summaries and the classifier through shared eligibility rules.

The title simulator uses the current model without creating or updating tasks. It shows recognized words, the resulting label (if any), and the first abstention rule that failed. It is not a historical prediction log: a simulation can differ from the result at the time a task was created because the history may have changed. Changing the title or training history invalidates the displayed analysis.

The automatic-classification switch is stored in DataStore. Turning it off skips classification at task creation but does not discard examples or stop collecting manual choices. Simulation remains available. Like theme preferences, this switch is independent of data backups and is not overwritten by import.

Reset can target category, priority, or both. It clears the selected labels from the independent history and sets exclusion flags on existing tasks without changing their values, confirmation provenance, completion status, or timestamps. Unrelated edits, status changes, and stale task copies cannot reactivate excluded examples. Explicitly choosing a field in the editor and saving makes that field eligible again. New tasks can supply new manual examples.

Migration 4 → 5 preserves existing eligibility. Version 5 backups preserve exclusions, so restoring a post-reset backup does not revive the excluded training data. Restoring an older backup deliberately restores the history captured at that time, before the reset.

Migration 5 → 6 seeds the independent history from remaining eligible tasks. Tasks deleted before this migration cannot be recovered without a prior backup. Deleting a category clears its historical category labels while preserving priority learning.

Migration 6 → 7 converts all retained titles, including already deleted tasks, into unordered word counts and removes the title column. Importing a version 6 backup performs the same conversion. Existing exported files are not rewritten; they may still contain the original text. These changes describe logical application data removal, not a guarantee of forensic erasure from storage or external backups.
