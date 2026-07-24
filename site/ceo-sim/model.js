/* The Decider — simulation model + content.
   Pure logic, no DOM: game.js drives it, and it can run headless under
   node for tuning (see repo scratch tests). All numbers are k$ / weeks. */
(function (root) {
  'use strict';

  // Deterministic per-run jitter so replays feel alive but endings land
  // on schedule.
  function lcg(seed) {
    let s = seed >>> 0;
    return function () {
      s = (s * 1664525 + 1013904223) >>> 0;
      return s / 4294967296;
    };
  }

  const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

  // ---------------------------------------------------------------- cards
  // Every decision in the game is sorted by the two questions that matter:
  // how bad if wrong (stakes), and can we undo it (oneWay).
  const CARDS = [
    { id: 'db', t: 'Choose the production database', d: 'Eng is split between Boring & Proven and Shiny & New. Migrating later means rewriting everything while it is on fire.', stakes: 'HIGH', oneWay: true,
      take: 'You read both proposals, pick Boring & Proven, and write down why. Eng grumbles for a week, then builds on rock.',
      del: 'You wave it through. Somewhere, a one-way door quietly swings open.',
      disaster: { delay: 22, cash: 350, door: 'DATABASE', text: 'FIRE: The database bet from months ago has hit its ceiling. Migration will take two quarters and most of your will to live.' } },
    { id: 'snacks', t: 'Office snack vendor renewal', d: 'The kombucha faction and the jerky faction have both submitted decks.', stakes: 'LOW', oneWay: false,
      take: 'You spend an evening on snack analytics. The jerky wins. So does entropy — 30 more calls like this are now yours.',
      del: 'Office manager picks. If it is wrong, it is wrong for one month. Nobody remembers by Friday.' },
    { id: 'series', t: 'Term sheet for the growth round', d: 'The lead investor wants a board seat and a 2x liquidation preference. This paragraph will outlive everyone in the building.', stakes: 'HIGH', oneWay: true,
      take: 'You negotiate the preference down yourself. Two sentences changed; the company keeps its future.',
      del: 'Doug "handles it". Doug is great with spreadsheets. This was not a spreadsheet.',
      disaster: { delay: 26, cash: 400, door: 'TERM SHEET', text: 'FIRE: That liquidation preference nobody read is now steering the company. The steering is toward a cliff.' } },
    { id: 'font', t: 'Homepage headline font', d: 'Marketing has narrowed it to two serifs. They are, to within measurement error, the same serif.', stakes: 'LOW', oneWay: false,
      take: 'You have opinions. You share all of them. The font ships two weeks late, indistinguishable from either option.',
      del: 'Design picks one. It can be changed next sprint by editing one line. It will not need to be.' },
    { id: 'vpeng', t: 'Hire the VP of Engineering', d: 'Whoever gets this job will hire the next forty people and set the culture they hire into.', stakes: 'HIGH', oneWay: true,
      take: 'You run the final loop yourself and check references personally. Culture compounds — you just picked its author.',
      del: 'A committee optimizes for "no strong objections". You get the most agreeable candidate, which is a different thing from the best one.',
      disaster: { delay: 20, cash: 250, door: 'VP HIRE', text: 'FIRE: The consensus VP hire has spent two quarters reorganizing the reorg. The best engineers are updating their resumes alphabetically.' } },
    { id: 'ads', t: 'Split the quarterly ad budget', d: 'Search vs. podcasts vs. a billboard Chad is emotionally invested in.', stakes: 'LOW', oneWay: false,
      take: 'You personally reallocate 6% between channels. The meeting costs more than the 6%.',
      del: 'Marketing runs it as an experiment and moves money monthly. Reversible, measurable, not your problem.' },
    { id: 'pricing', t: 'Pricing model: subscription vs. lifetime', d: 'Once thousands of customers hold "unlimited forever" receipts, forever is the business model.', stakes: 'HIGH', oneWay: true,
      take: 'You model both futures and pick the one the company can survive. Boring price, durable company.',
      del: 'Sales picks whatever closes this quarter. This quarter goes great.',
      disaster: { delay: 24, cash: 300, door: 'PRICING', text: 'FIRE: The "unlimited forever" plans have matured. Customers cost more than they pay, in perpetuity, contractually.' } },
    { id: 'rename', t: 'Rename "Settings" to "Preferences"', d: 'A four-message thread has become a forty-message thread.', stakes: 'LOW', oneWay: false,
      take: 'You weigh in. The thread doubles again, now with your name in it.',
      del: 'Product ships one, watches support tickets, keeps it. Total cost: one string.' },
    { id: 'distro', t: '10-year exclusive distribution deal', d: 'Guaranteed volume, one partner, a decade of no exits. The word "exclusive" is doing a lot of work.', stakes: 'HIGH', oneWay: true,
      take: 'You cut it to three years with performance outs. Less headline, more future.',
      del: 'Chad signs by Friday to make the quarter. The decade begins.',
      disaster: { delay: 18, cash: 350, door: 'EXCLUSIVE', text: 'FIRE: Your exclusive distribution partner has pivoted to artisanal candles. You are contractually along for the ride until 2036.' } },
    { id: 'keg', t: "The intern's kombucha keg proposal", d: 'Kevin has a slide deck. Slide three is just the word "SYNERGY" over a photo of a keg.', stakes: 'LOW', oneWay: false,
      take: 'You schedule a tasting. Your calendar now contains the word "kombucha" and your dignity does not.',
      del: 'Facilities says yes to a one-month trial. Kevin is thrilled. The blast radius is a fridge shelf.' },
    { id: 'sunset', t: 'Sunset the original Slab Classic', d: 'It is 4% of revenue, 40% of support tickets, and 100% of the early adopters who made you.', stakes: 'HIGH', oneWay: true,
      take: 'You decide the how as much as the whether: long runway, trade-in program, a personal letter. Loyalty survives the funeral.',
      del: 'A PM ships a deprecation banner on a Tuesday. The early adopters notice you did not sign it.',
      disaster: { delay: 16, cash: 200, door: 'SUNSET', text: 'FIRE: The abrupt Slab Classic shutdown is now a cautionary thread with 40,000 upvotes. Trust, it turns out, was load-bearing.' } },
    { id: 'offsite', t: 'Pick the team offsite location', d: 'Mountains vs. beach. There is a spreadsheet. There should not be a spreadsheet.', stakes: 'LOW', oneWay: false,
      take: 'You break the tie personally. Both factions now know whose side you are on re: sand.',
      del: 'They vote. The beach wins. It rains. Everyone bonds over the rain. Offsite: successful.' },
    { id: 'oss', t: 'Open-source the firmware', d: 'Once the code is public, it is public in every future. Community goodwill vs. competitors reading your homework.', stakes: 'HIGH', oneWay: true,
      take: 'You decide what opens, what stays closed, and why — on the record. A strategy, not an accident.',
      del: 'An enthusiastic engineer pushes the repo on a Friday. The license file says TODO.',
      disaster: { delay: 20, cash: 250, door: 'FIRMWARE', text: 'FIRE: A competitor ships a clone built on your accidentally-open firmware. Their changelog thanks you by name.' } },
    { id: 'tracker', t: 'Standardize the bug tracker', d: 'Three teams, three trackers, one bug filed in all three with three severities.', stakes: 'LOW', oneWay: false,
      take: 'You evaluate seven tools. The eighth tab of your comparison sheet gains sentience.',
      del: 'Eng leads pick one and migrate over a sprint. Worst case, they pick again next year.' },
    { id: 'acq', t: 'Acquire the tiny rival "Moonbeam Jr."', d: 'Their team is great. Their codebase is a mystery. Acquisitions are famously easy to undo, said no one.', stakes: 'HIGH', oneWay: true,
      take: 'You do the diligence yourself and buy the team, not the mystery. Integration has an owner and a deadline.',
      del: 'Corp dev "runs point". The deck said synergy; the codebase says Perl.',
      disaster: { delay: 18, cash: 400, door: 'ACQUISITION', text: 'FIRE: The Moonbeam Jr. acquisition has produced two of everything and one of nothing. The synergy is negative.' } },
    { id: 'sprint', t: 'Choose the sprint length', d: 'One week vs. two. Both camps cite the same blog post.', stakes: 'LOW', oneWay: false,
      take: 'You attend a ceremony-design workshop. It has ceremonies of its own.',
      del: 'Teams try both for a month and keep what ships. The blog post is never cited again.' },
    { id: 'retention', t: 'Customer data retention policy', d: 'What you keep, you can leak. What you promise, you must keep. Regulators have opinions and subpoenas.', stakes: 'HIGH', oneWay: true,
      take: 'You set the policy: keep little, encrypt everything, write it down. Dull, cheap, unbreachable.',
      del: '"Default settings are probably fine." The default is: keep everything, forever, in one bucket.',
      disaster: { delay: 22, cash: 450, door: 'DATA', text: 'FIRE: The breach found nine years of data nobody decided to keep. The fine is per record. There are many records.' } },
    { id: 'logo', t: 'Approve the new logo tweak', d: 'The sun in the logo is being rotated 4 degrees. There are strong feelings.', stakes: 'LOW', oneWay: false,
      take: 'You request three more angles. The sun has now been rotated more than the actual sun.',
      del: 'Design ships it. Zero customers notice. The sun rises anyway.' },

    // The tricky ones — in Balance mode the stamps are hidden, and these
    // read as the opposite of what they are. Judgment, not rule-following.
    { id: 'apirename', t: 'Tidy up: rename the v1 API endpoints', d: 'The old names are ugly and inconsistent; the new ones are much nicer. Three hundred external developers have built against the old ones.', stakes: 'HIGH', oneWay: true, tricky: true,
      take: 'You ask one question — "who depends on this?" — and the tidy-up becomes a versioned migration with a two-year sunset. Boring. Correct.',
      del: 'The names are much nicer now. Somewhere, three hundred integrations quietly stop working.',
      disaster: { delay: 16, cash: 300, door: 'API BREAK', text: 'FIRE: The API tidy-up broke every partner integration at once. The partners have opinions. The opinions have lawyers.' } },
    { id: 'bigco', t: 'The "strategic partnership" with OmniCorp', d: 'A breathless deck, an enormous logo, a co-marketing plan — and, down in clause 14, a mutual 30-day exit for either side, no penalty.', stakes: 'LOW', oneWay: false, tricky: true,
      take: 'You spend three weeks personally negotiating a contract either side can leave in a month. The logo was doing all of the work.',
      del: 'Chad runs it. If it sours, clause 14 is the door out — and it swings both ways, cheaply.' },
    { id: 'freetier', t: 'Growth hack: 10x the free tier', d: 'A quick lever to juice signups before the conference. Nobody in recorded history has successfully shrunk a free tier back down.', stakes: 'HIGH', oneWay: true, tricky: true,
      take: 'You keep the tier and ship a 30-day trial instead — generosity with an expiry date. Signups still jump.',
      del: 'Signups jump! The tier is now a load-bearing public expectation. Entitlements ratchet one way.',
      disaster: { delay: 20, cash: 250, door: 'FREE TIER', text: 'FIRE: The 10x free tier is now the whole product for 80% of users. Shrinking it makes the internet extremely loud.' } },
    { id: 'contractor', t: 'Contractors for the holiday rush', d: 'Support is drowning. A staffing agency can have five people started Monday, on 90-day terms.', stakes: 'LOW', oneWay: false, tricky: true,
      take: 'You interview all five personally. It is December. The support queue watches you do this.',
      del: 'Ops signs the 90-day terms. If it doesn\'t work, it un-happens in March all by itself.' },
    { id: 'selfhost', t: 'Let the big customer self-host "just this once"', d: 'A seven-figure deal if they can run it on their own servers. A second copy of the product would then exist, forever, aging separately.', stakes: 'HIGH', oneWay: true, tricky: true,
      take: 'You say no to the fork and yes to a dedicated instance. The deal shrinks a little; the codebase stays one thing.',
      del: 'The deal closes! There are now two products. One of them is invisible, distant, and slowly diverging.',
      disaster: { delay: 22, cash: 350, door: 'THE FORK', text: 'FIRE: The self-hosted fork needs a security patch you cannot ship, on servers you cannot see, for a customer you cannot lose.' } },
    { id: 'lease', t: 'The office lease renewal', d: 'Facilities calls it paperwork. The renewal on the table is five years, personally guaranteed. Month-to-month costs 15% more.', stakes: 'HIGH', oneWay: true, tricky: true,
      take: 'You pay the 15% for month-to-month. Optionality has a price — you check it against the five-year price of being wrong, and it\'s cheap.',
      del: '"It\'s just paperwork." The company now lives here until 2031, whatever size it turns out to be.',
      disaster: { delay: 24, cash: 280, door: 'THE LEASE', text: 'FIRE: The office fits sixty. You are thirty, shrinking, and personally guaranteed until 2031.' } }
  ];

  // The 60-second sorting test — before your first run and after beating
  // the Balance, sort six decisions. The delta is the game's report card.
  const SORT_TEST = [
    { t: 'Approve every blog post before it publishes', ceo: false,
      why: 'Reversible, low stakes, high frequency — the definition of a queue-builder. Set the voice once, review after.' },
    { t: 'Choose between two acquisition offers for the company', ceo: true,
      why: 'Bet-the-company and permanently irreversible. This is one of the handful of decisions that is actually the job.' },
    { t: 'Set the refund policy for last week\'s launch glitch', ceo: false,
      why: 'Bounded cost, fully reversible next week, and support has more context than you do.' },
    { t: 'Pick the company\'s second product line', ceo: true,
      why: 'Years of compounding lock-in — talent, brand, capital. A one-way door wearing a roadmap costume.' },
    { t: 'Approve a mid-level engineer\'s promotion', ceo: false,
      why: 'Their manager has the context; a wrong call is correctable next cycle. Calibrate the bar, not each case.' },
    { t: 'Decide whether to keep user analytics data forever', ceo: true,
      why: 'Quiet, technical-looking, and irreversible — what you keep, you can leak, and liability compounds silently.' }
  ];

  // -------------------------------------------------------------- scripts
  // Scripted beats: {w: week, who, t: text, sfx, major, cash, morale, fire, door}
  // who omitted = narrator. major beats are collected for the front page.
  const OPENING = { w: 1, t: 'Sunbeam Systems, Inc. — maker of the Slab™, a paper-like tablet you can read in the sun. 40 people, one building, cash in the bank, and you in the corner office.', sfx: 'page' };

  const SCRIPT_A = [
    OPENING,
    { w: 2, who: 'YOU', t: '"A CEO\'s job is to make the best decisions. I am the best decision-maker here. Therefore every decision is my job. The logic is flawless."', major: true },
    { w: 3, t: 'You pick the launch date. It is the right date. Ka-ching.', sfx: 'kaching' },
    { w: 5, who: 'KEVIN', t: '"The CEO approved my code AND fixed my semicolon personally. What a guy."' },
    { w: 7, t: 'You catch a pricing error Sales missed — saves $80k. See? This is why you decide everything.', sfx: 'kaching', major: true, cash: 80 },
    { w: 9, who: 'CHAD', t: '"Boss picked the CRM himself. Honestly? Great CRM."' },
    { w: 11, t: 'You choose the office coffee. It is excellent. Everyone agrees, to your face.' },
    { w: 13, t: 'Q1 closes strong — best quarter ever. The system works. You ARE the system.', sfx: 'kaching', major: true },
    { w: 16, t: 'You now approve: hires, prices, fonts, snack layouts, and the wording of one (1) birthday card.' },
    { w: 19, who: 'PRIYA', t: '"Quick one — can we ship the fix Friday?" You: "Let me think about it." It is Tuesday. There are three Fridays in the queue ahead of hers.' },
    { w: 22, t: 'Your calendar is now a single recurring meeting named "Decisions". It runs 9am–9pm.', sfx: 'paper' },
    { w: 25, t: 'Decision queue: 19. Priya\'s team invents "waiting-driven development".', sfx: 'paper' },
    { w: 28, who: 'MARGARET', t: '"I had an idea today. Then I remembered ideas go in the queue, and I put it back in my head."' },
    { w: 31, t: 'The button-color decision is now 11 days old. It has aged like milk. The button no longer matters.', major: true },
    { w: 34, who: 'KEVIN', t: '"Asked if I could reboot staging. He said he\'ll get back to me Thursday. Which Thursday: unclear."' },
    { w: 37, t: 'FIRE: Competitor Moonbeam ships first. Their CEO reportedly "lets people do things."', sfx: 'alarm', major: true, cash: -150, fire: 'PRODUCT' },
    { w: 40, t: 'You start deciding faster to catch up. Quality dips. Nobody tells you — telling you is item #38 in the queue.' },
    { w: 43, who: 'DOUG', t: '"Approvals pending: 41. Including my request for a meeting to discuss the approvals backlog."' },
    { w: 46, t: '2 a.m. You approve a $1.8M contract you did not read. It contains a llama clause.', sfx: 'thud', major: true, cash: -180 },
    { w: 49, t: 'FIRE: The big retail deal dies waiting for your signature. It was #23 in the queue, behind the birthday card.', sfx: 'alarm', major: true, cash: -250, fire: 'SALES' },
    { w: 52, t: 'Q4: growth flat. Your diagnosis: you must decide MORE, and FASTER. The queue files a dissenting opinion.', major: true },
    { w: 55, t: 'Margaret quits. Exit interview, in full: "I was a vending machine that dispensed slide decks."', sfx: 'thud', major: true, morale: -8 },
    { w: 58, t: 'Your three best engineers leave for Moonbeam. The ones who stay really, truly like being told what to do.', major: true, morale: -6 },
    { w: 61, who: 'KEVIN', t: '"New hires ask me how things get decided here. I point at the queue and whisper: they don\'t."' },
    { w: 64, t: 'FIRE: The factory needs a materials yes by Friday. Your Friday is fully booked deciding a favicon.', sfx: 'alarm', major: true, cash: -200, fire: 'OPS' },
    { w: 67, t: 'You dream in checkboxes now. The dreams are pending your approval.' },
    { w: 70, t: 'Nobody below VP has made a decision in ten months. Two managers can no longer order lunch unassisted.', major: true },
    { w: 74, t: 'FIRE: The payroll-processor switch — six weeks in queue — fails on payday. Payday is a strong day to fail.', sfx: 'alarm', major: true, cash: -150, morale: -12, fire: 'OPS' },
    { w: 78, who: 'DOUG', t: '"Runway: shrinking. I\'d walk you through the model, but the model needs your approval to open."' },
    { w: 82, t: 'You now make 40 decisions a day at the quality of a coin flip. The coin is tired.', major: true },
    { w: 86, t: 'The board requests a meeting. The request spent two weeks in your queue.', major: true },
    { w: 90, t: 'The board meets without you. There is one agenda item.', sfx: 'alarm', major: true }
  ];

  const SCRIPT_B = [
    OPENING,
    { w: 2, who: 'YOU', t: '"A CEO\'s job is vision. Details are for details people. I empower. I inspire. I\'m off to Lisbon — decide amongst yourselves."', major: true },
    { w: 4, t: 'Everything is shipping! Constantly! Nobody is entirely sure why, or whether the things fit together!', sfx: 'kaching' },
    { w: 6, who: 'PRIYA', t: '"No approvals needed. I found the bug, fixed it, shipped it, and took a nap. Utopia."' },
    { w: 8, t: 'Morale: incredible. Someone brought a kayak into the office. It\'s fine. It\'s probably fine.' },
    { w: 10, t: 'Chad closes a monster deal by promising a feature. Someone will eventually find out which feature.', sfx: 'kaching', major: true, cash: 150 },
    { w: 13, t: 'Q1: record speed. You accept the credit remotely, from a boat.', sfx: 'kaching', major: true },
    { w: 16, t: 'Eng picks a hot new database. Product independently picks a different hot new database. Both are confident. Neither knows.' },
    { w: 19, who: 'KEVIN', t: '"I deployed on my second day! To production! Which production? Great question!"' },
    { w: 22, t: 'Doug approves 14 SaaS subscriptions, including three whose only job is monitoring the other eleven.' },
    { w: 26, t: 'Sales starts selling "unlimited forever" plans. Forever, it will turn out, is quite long.', major: true },
    { w: 30, t: 'Two teams discover they have built the same feature for five months. They merge it. It now has two settings menus.', sfx: 'thud', major: true, cash: -150, fire: 'ENG' },
    { w: 34, who: 'MARGARET', t: '"I asked who owns pricing. Three people said \'me\' simultaneously. They have scheduled a war."' },
    { w: 38, t: 'The rebrand ships. Half the site says Sunbeam, half says SUNBM — the vowels tested poorly with one intern.' },
    { w: 42, t: 'FIRE: Chad signs a 10-year exclusive with a mall kiosk chain. The mall is scheduled for demolition.', sfx: 'alarm', major: true, cash: -300, fire: 'SALES', door: 'EXCLUSIVE' },
    { w: 46, t: 'Eng rewrites the backend in a language only Priya\'s cousin reads fluently. The cousin now works at Moonbeam.' },
    { w: 50, t: 'You visit the office. Your badge doesn\'t work. Security asks which company you\'re with. Genuinely a fair question.', major: true },
    { w: 54, t: 'Q: Who decided the data retention policy? A: Nobody. It decided itself. It decided badly.', major: true, door: 'DATA' },
    { w: 58, t: 'FIRE: The database migration was a one-way door. It opened onto a cliff.', sfx: 'alarm', major: true, cash: -350, fire: 'ENG', door: 'DATABASE' },
    { w: 62, who: 'DOUG', t: '"The cash forecast has three columns: best case, worst case, and \'depends who you ask\'."' },
    { w: 66, t: 'Two VPs are now openly at war. The all-hands has a seating chart and a mediator.', major: true, morale: -8 },
    { w: 70, t: 'FIRE: Security "wasn\'t anyone\'s decision." The breach was, briefly, everyone\'s.', sfx: 'alarm', major: true, cash: -380, morale: -12, fire: 'OPS' },
    { w: 74, who: 'YOU', t: '"How did we buy a llama farm?" Doug: "You weren\'t at the meeting." You: "Which meeting?" Doug: "Any of them."', sfx: 'thud', major: true, cash: -250 },
    { w: 78, t: 'Customers now describe the product, affectionately, as "four startups in a trench coat".', major: true },
    { w: 82, t: 'The Q2 pricing decision is now load-bearing. It cannot be moved. It is also wrong.', major: true, door: 'PRICING' },
    { w: 86, t: 'Priya quits — not angry, just "tired of winning the same argument weekly, best-of-infinity."', sfx: 'thud', major: true, morale: -10 },
    { w: 90, t: 'FIRE: The "unlimited forever" customers have found the fine print. There is no fine print.', sfx: 'alarm', major: true, cash: -230, fire: 'SALES' },
    { w: 94, t: 'You return full-time to "align the org". The org has four strategies. Each strategy has its own hoodie.', major: true },
    { w: 98, t: 'Emergency all-hands. You make your first decision in two years. Nobody can tell whether it is binding.', major: true },
    { w: 102, t: 'The board offers to "help you decide about the future". It is not, structurally, an offer.', sfx: 'alarm', major: true }
  ];

  const SCRIPT_D = [
    OPENING,
    { w: 2, who: 'YOU', t: '"I\'ve read the books. I delegate everything now — snacks, sprints, fonts, the lot. I keep only what\'s critical." (A reasonable number of things are critical. A very reasonable number.)', major: true },
    { w: 5, t: 'You delegate 34 decisions in a single week. It feels incredible. You keep: pricing, roadmap, senior hires, brand, partnerships, and "anything customer-visible."' },
    { w: 9, who: 'KEVIN', t: '"The boss let me pick the standup time! So empowered. Anyway, my actual project is waiting on his roadmap review."' },
    { w: 13, t: 'Q1 closes strong. Delegation dashboard: 91% of decisions made without you. Leverage dashboard: does not exist.', sfx: 'kaching', major: true },
    { w: 17, t: 'Margaret\'s pricing proposal arrives. You "just want one more pass." Version 2 is due next week. So, therefore, is version 3.' },
    { w: 22, who: 'PRIYA', t: '"I can decide anything under $10k. Everything I actually work on costs more than $10k."' },
    { w: 27, t: 'Your review adds two weeks and improves things 4%. Everyone has done this math. Everyone politely declines to say it.' },
    { w: 33, t: 'Only five items in your queue. Each has been there six weeks. It\'s not a queue, it\'s a residency program.', sfx: 'paper', major: true },
    { w: 39, who: 'MARGARET', t: '"I write proposals in his voice now. First-pass approval up 40%. Am I a VP or a very expensive autocomplete?"' },
    { w: 45, t: 'Moonbeam ships their v2. Your v2 is better and one review cycle from done. It has been one review cycle from done since March.', major: true },
    { w: 52, t: 'Year one closes: solid, not spectacular. "We\'re being disciplined," you say. Discipline is one of the words for it.' },
    { w: 58, t: 'This month you delegated the offsite, the logo, and the bug tracker. You personally rewrote the homepage headline at 1 a.m.' },
    { w: 64, who: 'CHAD', t: '"Big deal needs custom pricing, needs your sign-off—" (waiting) "—still waiting—" (waiting) "—they went with Moonbeam."', sfx: 'thud', major: true, cash: -150 },
    { w: 71, t: 'The board asks what you\'d do with more leverage. You show them your delegation stats. They were asking about the other 6%.' },
    { w: 78, who: 'PRIYA', t: '"Third Head-of-Design candidate withdrew. Six interviews, then \'founder final round\'. Scheduling window: geological."' },
    { w: 85, t: 'Fires this quarter: zero. Growth this quarter: also zero. It\'s very calm here. Calm like a pond. Ponds don\'t go anywhere.', major: true },
    { w: 92, t: 'You approve roadmap v9. It is 96% identical to v1, submitted in February.', sfx: 'paper', major: true },
    { w: 100, t: 'Year two: Moonbeam is now bigger than you. Not better — faster. Their CEO makes fewer, worse decisions, sooner.', major: true },
    { w: 108, t: 'Margaret quits — gently. Exit interview: "Nothing was wrong. That\'s sort of the problem. I\'d like to make a call before I\'m forty."', sfx: 'thud', major: true, morale: -8 },
    { w: 116, t: 'Her replacement asks what the role\'s real decision scope is. Everyone, silently, in unison, looks at your office.' },
    { w: 124, t: 'Breakthrough: you raise your delegation threshold to $50k! The critical list, examined closely, has not changed by one item.', major: true },
    { w: 132, who: 'DOUG', t: '"Growth: 4%. Payroll growth: 11%. I\'ve put these two numbers next to each other in the deck, as a bit."' },
    { w: 140, t: 'A brilliant partnership memo waits five weeks for your pass. The partner\'s window was four.', sfx: 'alarm', major: true, cash: -200, fire: 'SALES' },
    { w: 150, t: 'Nobody is angry. Nobody is leaving in droves. The company is fine. "Fine" is doing unpaid overtime in every sentence about you.' },
    { w: 160, who: 'KEVIN', t: '"Three years here. I\'ve shipped everything I was allowed to decide. Both things."' },
    { w: 170, t: 'Moonbeam offers to acquire Sunbeam. The offer is fair. That\'s the insulting part.', sfx: 'thud', major: true },
    { w: 180, t: 'You run the diligence personally. It\'s the fastest anything has moved here in years. Everyone notices what that says.', major: true },
    { w: 192, t: 'The board votes yes. You have the final say. You always did.', major: true }
  ];

  const SCRIPT_E = [
    OPENING,
    { w: 2, who: 'YOU', t: '"Culture eats strategy for breakfast. I hire great people and hand them real decisions — that\'s how they grow. Baton up."', major: true },
    { w: 6, t: 'You give Priya the platform decision "to own end-to-end." She is thrilled. She has 60% of your context and 100% of your encouragement.' },
    { w: 11, who: 'KEVIN', t: '"The CEO said my kombucha keg taught me \'ownership\'. I run lifecycle pricing now. Same energy, he says."' },
    { w: 15, t: 'Q1: good quarter! Morale: spectacular. Someone has needlepointed the company values.', sfx: 'kaching', major: true },
    { w: 21, t: 'The pricing committee lands on the midpoint of three positions. The market was not consulted about the midpoint.' },
    { w: 28, who: 'MARGARET', t: '"We aligned on a direction everyone can live with. Living-with-things is now the strategy."' },
    { w: 35, t: 'Chad\'s key-accounts plan is 70% as good as yours would have been. Nobody says this number out loud. Saying it would be unsupportive.', major: true },
    { w: 42, t: 'You spot a real flaw in the launch plan. You write three paragraphs of encouraging questions instead of the one sentence of no.', major: true },
    { w: 50, t: 'Year one: growth decent, Glassdoor 4.9. The reviews mention "trust" eleven times and "results" twice.', major: true },
    { w: 57, t: 'The beloved-but-doomed product line survives its third review. Killing it would crush the team that loves it. It is crushing the P&L instead.', sfx: 'thud', major: true, cash: -150 },
    { w: 64, who: 'PRIYA', t: '"I made the platform call! ...I made it right? — asking for the confident version of me you promoted."' },
    { w: 72, t: 'A VP is wrong in a meeting. Everyone can tell. The moment passes in warm nods. You practice radical candor about the snacks.' },
    { w: 80, t: 'Margins slip three points. No single decision did it. Forty did — each 70% as good as yours, none reviewed.', major: true },
    { w: 88, who: 'DOUG', t: '"I benchmarked us against Moonbeam. May I present it after lunch, when the room\'s blood sugar can absorb it?"' },
    { w: 96, t: 'Year two: flat. "A foundation year," says the deck. The foundation is now three foundations deep.', major: true },
    { w: 105, t: 'The exec who isn\'t working out is beloved. The org would "take it hard." You take another quarter instead.', major: true },
    { w: 113, who: 'KEVIN', t: '"I was told this project would stretch me. I have located my limits. They were two decisions ago."' },
    { w: 122, t: 'Churn ticks up. The exit surveys praise your team\'s kindness on the way out the door.', sfx: 'thud', cash: -100 },
    { w: 130, t: 'You overrule a committee — once. The relief in the room is enormous. Everyone privately wanted the grown-up to decide.', major: true },
    { w: 140, t: 'It does not become a habit.', major: true },
    { w: 150, t: 'The down round is dressed as a "flat extension." The culture deck gains a slide about resilience.', sfx: 'alarm', major: true, cash: 500 },
    { w: 160, who: 'MARGARET', t: '"The new VP we poached from Moonbeam lasted five weeks. Said decisions here \'go to die in warm rooms.\' We threw her a lovely farewell."' },
    { w: 170, t: 'The hard call from year one is still unmade. It has compounded. Hard calls are the only thing that appreciates here.', sfx: 'thud', major: true, cash: -200 },
    { w: 180, t: 'The team still loves you. Genuinely. It is the most successful thing the company makes.', major: true },
    { w: 192, t: 'Q: "What would you do differently?" A, after a long pause: "Fewer growth opportunities. More decisions."', major: true }
  ];

  const SCRIPT_F = [
    OPENING,
    { w: 2, who: 'YOU', t: '"No philosophy this time. Just a dial, six instruments, and my attention. The dial says how much of this company crosses my desk."', major: true },
    { w: 6, t: 'Twelve people knew the plan by lunch. At this size your taste IS the product — the dial belongs high, for now. "For now" is the whole game.' },
    { w: 14, who: 'KEVIN', t: '"The CEO reviewed my landing page personally. Fixed the headline in nine minutes. Honestly? It\'s better. Weird little company."' },
    { w: 26, t: 'Headcount is climbing. Somewhere around here the math quietly changes, and nobody sends a memo when it does.', major: true },
    { w: 40, who: 'MARGARET', t: '"We hired nine people this month. Three of them asked me who approves things. I just said \'yes\' and walked away confidently."' },
    { w: 55, t: 'FIRE: A bad battery batch in the field. A recall is not a delegation exercise — for exactly as long as the fire burns, and not one week longer.', sfx: 'alarm', major: true, fire: 'OPS' },
    { w: 70, t: 'The recall is winding down. Quiet question from the back: the dial you turned up for the crisis — did you remember to turn it back?', major: true },
    { w: 85, who: 'DOUG', t: '"Fun fact: your calendar is simultaneously the company\'s scarcest resource and its most popular meeting room."' },
    { w: 100, t: 'Headcount 90. You can no longer read every PR, meet every customer, taste every batch. The org you have is the instrument you fly.', major: true },
    { w: 118, who: 'PRIYA', t: '"The teams you trusted first are the ones you trust most now. Funny how that compounds. Almost like a lesson."' },
    { w: 140, t: 'FIRE: A rival poaches a key exec overnight. How much this hurts is exactly how much the org learned before today.', sfx: 'alarm', major: true, fire: 'PRODUCT' },
    { w: 160, t: 'Nobody has asked you to settle an argument in a month. Either the org has matured, or it has given up asking. The difference is everything — check which.', major: true },
    { w: 178, who: 'CHAD', t: '"Closed the year\'s biggest deal without you even knowing it was in play. You\'re welcome. Also: sorry? Unclear. You\'re welcome."' },
    { w: 189, t: 'You make about six calls a quarter now. Each one deserves all of you. The other nine hundred happen near the facts, the way water finds a drain.', major: true }
  ];

  const F_DRIFT_FIRES = [
    'FIRE (drift): Two teams just discovered each other\'s identical project at the demo. Which way is the dial wrong?',
    'FIRE (drift): A "quick pricing experiment" shipped to everyone, permanently. Nobody remembers deciding it.',
    'FIRE (drift): The new hire\'s first week produced a press release. Legal found out from the press.',
    'FIRE (drift): Three roadmaps, all plausible, all incompatible, all funded. The dial has been too loose for a while.'
  ];

  const SCRIPT_C = [
    OPENING,
    { w: 2, who: 'YOU', t: '"New rule. I make the few decisions only I can make — the one-way doors. Everyone else decides everything else: fast, out loud, and on the record."', major: true },
    { w: 5, t: 'You publish the Decision Memo: what comes to you (irreversible, cross-team, bet-the-company), what never does (everything you can undo for less than it costs to ask you).' },
    { w: 9, who: 'PRIYA', t: '"I made a call today WITHOUT a meeting. It felt illegal. Shipped Tuesday."' },
    { w: 15, t: 'Weekly decision review: you read choices AFTER they ship and comment on three. Feedback, it turns out, scales better than approval.' },
    { w: 24, who: 'KEVIN', t: '"I asked the boss to pick our sprint length. He asked if it was reversible. I said yes. He walked away mid-sentence. Inspiring, honestly."' },
    { w: 33, t: 'Margaret\'s pricing memo reads like you wrote it. Better, actually — she had the data. The principles are compounding.', sfx: 'kaching', major: true },
    { w: 45, who: 'CHAD', t: '"Lost a deal this week because we wouldn\'t promise forever-pricing. Boss called it \'a good loss\'. Weird guy. Great quarter though."' },
    { w: 57, t: 'A one-way door reaches you with a two-page memo, a recommendation, and a dissent attached. You decide in a day. This used to take a quarter.', major: true },
    { w: 69, who: 'DOUG', t: '"Forecast has one column now. I miss the drama. I do not miss the drama."' },
    { w: 81, t: 'Moonbeam ships something flashy. Your teams respond in two weeks without a single meeting reaching your calendar.', sfx: 'kaching', major: true },
    { w: 93, who: 'MARGARET', t: '"The new hires think it\'s normal that decisions have owners and deadlines. Don\'t tell them. Let them think every company works."' },
    { w: 105, t: 'You realize you have made six decisions this quarter. You sweated every one. The other nine hundred happened near the facts.', major: true },
    { w: 117, who: 'PRIYA', t: '"The junior engineers now write \'is this a one-way door?\' in their design docs, unprompted. The virus is airborne."' },
    { w: 129, t: 'The board meeting is forty minutes long. Nobody has ever been this bored, this profitably.', major: true },
    { w: 137, t: 'You take two weeks off. Nothing breaks. Nobody calls. This is what winning quietly sounds like.', sfx: 'ding', major: true },
    { w: 141, t: 'Someone asks what you actually do all day. You say "almost nothing, extremely carefully." It is the truth.', major: true }
  ];

  // Metric-threshold events: fired once when the predicate first turns true.
  const DYNAMIC = [
    { id: 'q15', modes: 'A', when: g => g.queue >= 15, t: 'The decision queue crosses 15. Somewhere, a team starts a betting pool on verdict dates.', sfx: 'paper' },
    { id: 'q30', modes: 'A', when: g => g.queue >= 30, t: 'Queue: 30. The pile on your desk is now visible in the building\'s structural survey.', sfx: 'paper', major: true },
    { id: 'q60', modes: 'A', when: g => g.queue >= 60, t: 'Queue: 60. Items at the bottom have begun to fossilize. A paleontologist has been consulted.', sfx: 'thud', major: true },
    { id: 'm50', when: g => g.morale <= 50, t: 'Morale slips below 50. The kudos channel is now just the word "congrats" echoing.', morale: 0 },
    { id: 'm30', when: g => g.morale <= 30, t: 'Morale: 30. The exit-interview calendar has a waitlist.', sfx: 'thud', major: true },
    { id: 'c60', modes: 'B', when: g => g.coherence <= 60, t: 'Coherence slips below 60%. Two roadmaps now cite each other as the competition.', sfx: 'paper' },
    { id: 'c35', modes: 'B', when: g => g.coherence <= 35, t: 'Coherence: 35%. The org chart is best understood as a weather system.', sfx: 'thud', major: true },
    { id: 'cash5', when: g => g.cash <= 500 && g.week > 20, t: 'DOUG: "Cash under $500k. I have stopped buying the good coffee. This is my version of a fire alarm."', sfx: 'alarm', major: true },
    { id: 'san30', when: g => g.sanity <= 30, t: 'You answer "approved" to a question about your own lunch order. It was not a yes/no question.', major: true }
  ];

  // ------------------------------------------------------------- endings
  const ENDINGS = {
    A: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM COLLAPSES UNDER PAPERWORK; FOUNDER FOUND ALIVE BENEATH QUEUE',
      sub: '"Every decision I made was excellent," insists former CEO. "Especially the four thousand that nobody was still waiting for."',
      lessonTitle: 'THE BOTTLENECK — what actually killed it',
      lessons: [
        'First order, you won: your decisions really were better. Second order, everyone else\'s decisions stopped existing — and a company is mostly everyone else.',
        'A queue compounds like debt. Every late decision delays ten downstream ones, and the interest is paid in missed markets.',
        'People who love deciding left; people who stayed learned helplessness. You didn\'t hire a passive org — you selected for one.',
        'Overload made you worse precisely when it mattered: the bottleneck\'s quality collapses exactly at peak load. You became the rate-limiting enzyme of the whole organism.',
        'You didn\'t make the best decisions. You made all of them — which was briefly the same thing, and then the opposite.'
      ]
    },
    B: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM SPLITS INTO FOUR COMPANIES, ALL NAMED SUNBEAM',
      sub: '"We moved fast," said everyone, in different directions.',
      lessonTitle: 'THE GHOST — what actually killed it',
      lessons: [
        'First order, you won: real speed, real morale, real shipping. Second order, incoherence compounded silently — four local optimums, zero global one.',
        'One-way doors got walked through casually: pricing, data, decade-long ink. Reversible mistakes wash out; irreversible ones accumulate like sediment, then like concrete.',
        'Principal–agent, live on stage: every VP optimized their kingdom honestly and locally. Nobody owned the whole, so the whole belonged to no one.',
        'Without an arbiter, disagreements don\'t resolve — they metastasize into politics, and politics eats the calendar that used to build things.',
        'You empowered everyone to steer. The ship got eight rudders and no keel.'
      ]
    },
    C_WIN: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM POSTS 12TH STRAIGHT GOOD QUARTER; FOUNDER "PLEASANTLY BORED"',
      sub: '"I make about six decisions a year," says CEO. "I sweat those. The rest happen near the facts."',
      lessonTitle: 'THE BALANCE — why this one lives',
      lessons: [
        'Sort every decision with two questions: how bad if wrong, and can we undo it? Irreversible-and-important comes to you. Everything else goes to whoever is closest to the facts.',
        'Delegate outcomes, not tasks — and review after, not before. Feedback compounds into judgment; approval compounds into a queue.',
        'Write your principles down until people can predict your call. Then they stop needing it — that\'s the org learning to decide, which beats you deciding.',
        'Watch your own queue like a vital sign. Bottleneck? Your bar for "critical" is too low. Constantly surprised? It\'s too high.',
        'Speed on reversible things buys you the right to be slow on irreversible things. That trade is the job. Everything else is either hoarding or hiding.'
      ]
    },
    D: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM ACQUIRED FOR "A FAIR PRICE," SAY ALL PARTIES, QUIETLY',
      sub: 'Founder praised for delegating 94% of all decisions; the other 6% could not be reached for comment.',
      lessonTitle: 'THE FINAL SAY — what actually capped it',
      essay: [
        'This is the one to study, because you had receipts. You delegated by the hundreds — snacks, sprints, fonts, offsites — and every audit of your calendar would have acquitted you. But leverage isn\'t distributed by count. A company\'s trajectory is set by a handful of heavy decisions a year, and you kept every one: pricing, roadmap, senior hires, brand, "anything customer-visible." Each hold was individually defensible — that\'s the trap. Stretch the definition of critical wide enough and you\'re the bottleneck again, just an invisible one, because the visible queue is short.',
        'Nothing here ever caught fire, and that was the problem: fires generate feedback, and you had none. Things merely took one extra pass. Your review added two weeks and four percent, your leaders learned to imitate your taste instead of growing their judgment, your best one left not angry but unused — and the market moved at its own speed while everything important in your building moved at yours. The cost never appeared on any dashboard, because the cost was compounding growth that simply didn\'t happen.',
        'The cartoon bottleneck dies fast, in flames, and learns. The subtle one dies slowly, comfortably, at a fair acquisition price — still explaining, correctly, truthfully, uselessly, that he delegated almost everything. Most founders don\'t fail like Option A. They fail like this: right about every individual decision, wrong about the only aggregate that mattered.'
      ],
      lessons: [
        'Count leverage, not decisions. Delegating 94% by number and 6% by weight is hoarding with better paperwork.',
        'Every "critical" is a claim on your bandwidth. If the critical list never shrinks as your people grow, the list is measuring your anxiety, not the stakes.',
        'A review that adds two weeks and 4% is negative-sum. Some calls need your standard; most need your speed, or better yet, your absence.',
        'The insidious failures give no feedback. No fires, no exits, no crisis — just a pond. If everything is calm and nothing compounds, that IS the alarm.',
        'The heavy calls cost emotional energy — that\'s exactly why you kept them and exactly why letting a few go would have bought the most leverage per unit of trust.'
      ]
    },
    E: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM RAISES "FLAT EXTENSION"; GLASSDOOR RATING HOLDS AT 4.9',
      sub: '"Best culture I\'ve ever worked in," says departing customer.',
      lessonTitle: 'THE CONDUCTOR — what actually stalled it',
      essay: [
        'You were never absent — that\'s what makes this one hard to see from inside. You were present, warm, generous with real decisions, fluent in every management book\'s best chapter. The error was a theory of people that was too kind. Handed the big call, people sometimes rise. But mostly: nobody cares like a founder, nobody carries your context or your scar tissue, nobody is incentivized in their bones the way you are — and a critical decision gifted as a "growth opportunity" gets graded on effort by everyone watching. The standard slipped about thirty percent per call, and no one said so out loud, because morale was wonderful and criticizing the outcome felt like criticizing the culture.',
        'Consensus did the rest. Committees make medium calls medium — the midpoint of three positions is a position the market never asked for — and they cannot make the hard calls at all. So the beloved-but-doomed product lived, the likable-but-wrong exec stayed, and the one decision from year one that most needed a dictator compounded quietly for three years. High morale became the anesthetic: the feedback loop that should have hurt, didn\'t. Everyone was happy. Nothing worked.',
        'The ghost\'s company shatters loudly and teaches its lesson fast. The conductor\'s just goes sideways — pleasantly, respectfully, for years — because "empowering people" is the failure mode that photographs best. The fix isn\'t coldness; it\'s calibrated cynicism: love your people, count honestly on their context, incentives, and experience, and be exactly as dictatorial as the decision\'s weight demands. On the heavy ones, a founder\'s unfair advantage — caring more, knowing more, having been wrong more — is not a bias to suppress. It\'s the asset.'
      ],
      lessons: [
        'Delegation is a bet, and the odds are set by context, incentive, and experience — not by how much you believe in someone. Believe in people; bet like an actuary.',
        'A critical decision is not a training ground. Grow people on reversible calls with real stakes, not on the ones that set the company\'s ceiling.',
        'Consensus averages; markets don\'t. The midpoint of three opinions is usually nobody\'s right answer.',
        'The hard calls — killing the beloved thing, moving on the beloved person — can only be made centrally. If they\'re "everyone\'s" they\'re no one\'s, and they appreciate with interest.',
        'Beware metrics that only measure comfort. A 4.9 culture score with flat growth means the feedback loop has been sedated, not that the org is healthy.'
      ]
    },
    F_WIN: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM BECOMES THE COMPANY OTHERS BENCHMARK; FOUNDER CREDITS "A DIAL AND A HABIT"',
      sub: '"I was never on the right setting," says CEO. "I was just never wrong for long."',
      lessonTitle: 'THE DIAL — the meta-lesson',
      essay: [
        'Here is the thing the four dead timelines were trying to tell you: there is no right setting. Anyone who gives you the correct percentage of decisions a CEO should make is selling a framework. The correct setting moved eleven times in this run alone — down as your people compounded, up hard when the batteries failed, down again the week the recall ended. A fixed philosophy — any fixed philosophy, including a wise one — is just a slower way to drift.',
        'And drift is silent, which is the actual danger. Both failure modes feel like virtue from the inside: gripping feels like diligence, releasing feels like leadership. The first-order effects of each arrive immediately and they are genuinely good — better calls, faster shipping — while the second-order bill arrives quarters later, addressed to someone you used to be. You cannot feel drift. You can only instrument for it.',
        'So the job is not "find the balance point." The job is: build the gauges, glance at them weekly, and correct early and small. Your queue is the grip gauge — if it grows, your bar for "critical" is too low. Your surprise rate is the trust gauge — if the company keeps startling you, it\'s too high. Write your principles down until people can predict your call; review after instead of before; recount the "critical" list every quarter, because it should shrink as your people grow and spike when the building is on fire. You will always be a little wrong. Winners are just wrong in a direction they\'re watching, briefly.'
      ],
      lessons: [
        'The dial is steered, not set. Company size, org maturity, and crisis all move the correct setting — on their schedule, not yours.',
        'Instrument for drift: queue length and decision latency catch over-grip; surprise rate and duplicate work catch over-trust.',
        'Correct early and small. A five-point turn this quarter beats a fifty-point reorg next year.',
        'Crisis centralizes, then you must give it back. The ratchet only sticks if you let it.',
        'Both ditches feel like virtue from inside the car. The gauges don\'t flatter; that\'s what they\'re for.'
      ]
    },
    F_DRIFT: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM SURVIVES FOUR YEARS OF WEATHER; CAPTAIN STILL LEARNING THE INSTRUMENTS',
      sub: 'Below: the dial you set, and the dial the company needed. Mind the gap.',
      lessonTitle: 'THE DIAL — what the chart is saying',
      essay: [
        'You lived — congratulations, sincerely; most timelines don\'t. But look at the two lines below. The dotted one is what the company needed, and it moved every time the company changed: down as people grew, sharply up in the crisis, down again after. The solid line is you. Every gap between them was paid for — in queue-weeks when you were above it, in quiet incoherence when you were below.',
        'The lesson isn\'t that you chose badly; it\'s that you corrected late. Drift is silent because its first-order effects feel great and its second-order effects arrive with the wrong postmark. The fix is boring and it works: watch the queue (grip gauge), watch your surprise rate (trust gauge), and move the dial five points the week the gauges twitch — not the quarter after the fire.'
      ],
      lessons: [
        'Surviving is not the same as steering. The gap between the lines below is the tuition you paid.',
        'Late small corrections become forced large ones. The reorg you eventually did was the dial-turn you skipped, with interest.',
        'The setting that saved you in the crisis was wrong a month later. Re-check the dial whenever the weather changes.'
      ]
    },
    F_LOSE: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM FOUND ON THE ROCKS; DIAL SET CONFIDENTLY THE ENTIRE WAY DOWN',
      sub: 'Investigators note the instruments were working. The looking was not.',
      lessonTitle: 'THE DIAL — read this before re-running',
      essay: [
        'The company didn\'t die of a bad setting; it died of a fixed one. The needs moved — headcount tripled, a crisis came and went — and the dial mostly didn\'t. Whichever ditch you favored, the mechanism was identical: the good news arrived immediately, the bill arrived later, and by the time it was undeniable the correction had to be huge.',
        'Run it again, and this time fly the gauges, not the philosophy: queue creeping up means loosen your definition of critical; getting surprised by your own company means tighten it. Five points at a time. The dial is steered, not set.'
      ],
      lessons: [
        'A fixed dial in a moving company is drift by definition.',
        'Watch queue and latency for over-grip; watch surprises and duplicates for over-trust.',
        'The gauges twitch quarters before the fires start. That lag is your entire margin.'
      ]
    },
    C_LOSE: {
      paper: 'THE DAILY SLAB',
      headline: 'SUNBEAM DISCOVERS EXCITING THIRD FAILURE MODE: RANDOM',
      sub: 'Company kept the wrong decisions and delegated the wrong ones; historians impressed by the range.',
      lessonTitle: 'THE SCRAMBLE — what went wrong',
      lessons: [
        'The two questions were on the box: how bad if wrong, and can we undo it? Hoard the irreversible, release the reversible. Mixing them up buys both failure modes at once.',
        'Every reversible decision you grabbed built your queue; every one-way door you waved through detonated on a delay. Note the delay — second-order effects always arrive after the applause.',
        'Play it again. The doors are labeled. They were always labeled.'
      ]
    }
  };

  // ------------------------------------------------------------- the sim
  function makeGame(mode, seed) {
    const g = {
      mode: mode,
      seed: (seed || 12345) >>> 0,
      rnd: lcg(seed || 12345),
      week: 0,
      over: false,
      ended: null,          // set to an ENDINGS key when done
      cash: 2500,           // k$
      revW: 52,             // k$/week, grows or decays
      headcount: 40,
      queue: 0,
      morale: mode === 'B' ? 80 : mode === 'E' ? 85 : mode === 'D' ? 68 : 70,
      sanity: 100,
      coherence: 100,
      learning: 0,
      speed: 1,
      quality: 0.95,
      firedIds: {},         // dynamic events already fired
      scriptIdx: 0,
      keyMoments: [],       // {w, t} majors, for the front page
      doors: [],            // one-way doors walked through (mode B/C)
      pendingDisasters: [], // mode C: {week, ...disaster}
      cGood: 0, cBad: 0,    // mode C scorecard
      cHoard: 0, cGhost: 0, // ...split by which ditch each miss leaned toward
      cMemoTax: 0,
      dial: 0.5,            // mode F: fraction of decisions that reach your desk
      devSum: 0, driftDebt: 0, driftIdx: 0,
      rival: 6,             // Moonbeam's stature — feeds on your growth deficit
      history: { cash: [], morale: [], health: [], dial: [], tstar: [] }
    };
    if (mode === 'F') g.morale = 72;
    g.script = { A: SCRIPT_A, B: SCRIPT_B, C: SCRIPT_C, D: SCRIPT_D, E: SCRIPT_E, F: SCRIPT_F }[mode];
    // Seed-shuffled decision deck: same seed, same memos in the same order —
    // so two players can run the same company and compare endings.
    g.deck = CARDS.slice();
    for (let i = g.deck.length - 1; i > 0; i--) {
      const j = Math.floor(g.rnd() * (i + 1));
      const tmp = g.deck[i]; g.deck[i] = g.deck[j]; g.deck[j] = tmp;
    }
    return g;
  }

  // Mode F: the setting the company actually needs, week by week. Falls as
  // the org matures, spikes for the crisis, falls again. The player never
  // sees it — until the ending chart.
  const TSTAR = [[0, 0.55], [40, 0.35], [52, 0.38], [58, 0.62], [76, 0.55], [100, 0.28], [130, 0.18], [192, 0.10]];
  function targetDial(w) {
    for (let i = 1; i < TSTAR.length; i++) {
      if (w <= TSTAR[i][0]) {
        const w0 = TSTAR[i - 1][0], v0 = TSTAR[i - 1][1], w1 = TSTAR[i][0], v1 = TSTAR[i][1];
        return v0 + (v1 - v0) * (w - w0) / (w1 - w0);
      }
    }
    return 0.10;
  }

  function health(g) {
    const cashPart = clamp(g.cash / 2500, 0, 1.4);
    return clamp(
      100 * (0.4 * cashPart + 0.3 * g.morale / 100 + 0.3 * g.coherence / 100),
      0, 120);
  }

  // Advance one week. Returns the list of events that happened.
  function tick(g) {
    if (g.over) return [];
    g.week++;
    const ev = [];
    const push = e => { ev.push(e); if (e.major) g.keyMoments.push({ w: e.w || g.week, t: e.t }); };

    // ---- mode dynamics ----
    if (g.mode === 'A') {
      const arrive = 2 + g.headcount * 0.16;
      const toCEO = arrive * 0.82;
      const cap = 6.5 * Math.max(0.3, g.sanity / 100);
      g.queue = Math.max(0, g.queue + toCEO - cap);
      const latency = g.queue / cap;
      g.sanity = clamp(g.sanity - (0.18 + g.queue * 0.022), 5, 100);
      g.quality = 0.45 + 0.55 * g.sanity / 100;
      g.speed = clamp(1.15 - latency * 0.1, 0.15, 1.15);
      g.morale = clamp(g.morale + (latency < 1.5 ? 0.1 : -(0.2 + Math.min(latency, 5) * 0.045)), 2, 100);
      if (g.morale > 60 && g.week % 3 === 0) g.headcount++;
      if (g.morale < 45 && g.week % 7 === 0) {
        g.headcount = Math.max(20, g.headcount - 2);
        push({ t: 'Two more resignations. Their goodbye email is one word: "deciding!"', sfx: 'thud', morale: 0 });
        g.morale = clamp(g.morale - 2, 2, 100);
      }
    } else if (g.mode === 'B') {
      g.queue = Math.max(0, g.queue - 2);
      g.coherence = clamp(g.coherence - (0.45 + g.headcount * 0.006), 5, 100);
      g.speed = 1.35 * (0.35 + 0.65 * g.coherence / 100);
      g.quality = 0.55 + 0.45 * g.coherence / 100;
      g.morale = clamp(g.morale + (g.coherence > 65 ? 0.15 : -(0.22 + (65 - g.coherence) * 0.012)), 2, 100);
      g.sanity = clamp(g.sanity - 0.02, 40, 100);  // you're fine. suspiciously fine.
      if (g.week % 2 === 0 && g.coherence > 40) g.headcount++;
    } else if (g.mode === 'D') {
      // The Final Say: delegates by count, hoards by weight. Small queue of
      // heavy items; every important thing waits exactly one pass too long.
      // Coherence here reads as "market edge" — it drifts to faster rivals.
      g.queue = clamp(g.queue + 0.18 - (g.week % 9 === 0 ? 1.1 : 0), 0, 16);
      g.sanity = clamp(g.sanity - 0.09, 35, 100);        // chronic, never acute
      g.quality = 0.95;                                  // your calls stay good — that's the alibi
      g.speed = clamp(0.92 - g.queue * 0.012, 0.55, 0.92);
      g.coherence = clamp(g.coherence - 0.3, 28, 100);
      g.morale = clamp(g.morale - 0.055, 20, 100);       // nobody's angry; everybody's a little less here
      if (g.week % 5 === 0 && g.headcount < 85) g.headcount++;
    } else if (g.mode === 'E') {
      // The Conductor: one strategy, warm rituals, high morale — and the
      // standard slipping a fraction of a percent per week, unremarked.
      g.queue = Math.max(0, g.queue - 2);
      g.coherence = clamp(g.coherence - 0.045, 80, 100);
      g.quality = clamp(g.quality - 0.0017, 0.6, 0.95);
      g.speed = 1.0;
      g.morale = clamp(g.morale + 0.02, 5, 100);          // the anesthetic
      g.sanity = clamp(g.sanity - 0.01, 60, 100);
      if (g.week % 3 === 0 && g.headcount < 95) g.headcount++;
    } else if (g.mode === 'F') {
      // Your Company: the dial is the player's; the target is the weather's.
      const Ts = targetDial(g.week);
      const dev = g.dial - Ts;
      g.devSum += Math.abs(dev);
      g.history.dial.push(g.dial); g.history.tstar.push(Ts);
      const arrive = 2 + g.headcount * 0.16;
      const cap = 7.5 * Math.max(0.35, g.sanity / 100);
      g.queue = Math.max(0, g.queue + arrive * g.dial - cap);
      const latency = g.queue / cap;
      g.sanity = clamp(100 - g.queue * 1.4, 25, 100);
      const under = Math.max(0, -dev), over = Math.max(0, dev);
      g.coherence = clamp(g.coherence + 0.35 - under * 5.5, 25, 100);
      g.learning = clamp(g.learning + 0.15 + (1 - g.dial) * 0.25 - over * 0.3, 0, 100);
      g.quality = clamp(0.95 - under * 0.5, 0.6, 0.95);
      g.speed = clamp(1.2 - latency * 0.12 - over * 0.25, 0.2, 1.2);
      g.morale = clamp(g.morale + (latency > 2 ? -0.35 : 0.06) - under * 0.15, 2, 100);
      if (g.morale > 58 && g.week % 3 === 0 && g.headcount < 110) g.headcount++;
      // silent ghost-side debt: pays out as drift fires
      g.driftDebt += Math.max(0, under - 0.04);
      if (g.driftDebt > 4) {
        g.driftDebt = 0;
        g.cash -= 130;
        g.coherence = clamp(g.coherence - 4, 25, 100);
        push({ t: F_DRIFT_FIRES[g.driftIdx++ % F_DRIFT_FIRES.length], sfx: 'alarm', major: true, fire: 'PRODUCT' });
      }
      // the crises: centralization saves the first; a taught org absorbs the second
      if (g.week === 58) {
        const hit = Math.max(60, 380 - 500 * Math.min(g.dial, 0.6));
        g.cash -= hit;
        push({ t: 'Recall bill: $' + fmtK(Math.round(hit)) + '. ' + (g.dial >= 0.45 ? 'You were on the bridge — it was contained.' : 'It ran wild for weeks before it reached your desk.'), sfx: 'thud', major: true });
      }
      if (g.week === 140) {
        const hit = Math.max(60, 300 - 2.5 * g.learning);
        g.cash -= hit;
        push({ t: 'The poaching costs $' + fmtK(Math.round(hit)) + '. ' + (g.learning > 60 ? 'The bench was deep; the org barely flinched.' : 'Nobody below them could catch what they dropped.'), sfx: 'thud', major: true });
      }
    } else { // C — trajectory driven by the player's sorting record
      const balance = clamp(0.75 + 0.06 * g.cGood - 0.12 * g.cBad, 0.25, 1.25);
      g.queue = Math.max(0, g.queue - 1.5);
      const latency = g.queue / 6.5;
      g.learning = clamp(g.learning + 0.1 + 0.05 * g.cGood, 0, 100);
      g.coherence = clamp(g.coherence + (balance > 0.8 ? 0.15 : -0.3) - latency * 0.05, 10, 100);
      g.speed = clamp(1.1 * balance * (0.5 + 0.5 * g.coherence / 100) - latency * 0.08, 0.2, 1.3);
      g.quality = 0.7 + 0.3 * Math.min(1, g.learning / 60);
      g.sanity = clamp(100 - g.queue * 1.5, 30, 100);
      g.morale = clamp(g.morale + (g.speed > 0.85 ? 0.12 : -0.15), 5, 100);
      if (g.morale > 60 && g.week % 3 === 0) g.headcount++;
      // delayed one-way-door detonations from bad delegations
      for (let i = g.pendingDisasters.length - 1; i >= 0; i--) {
        const d = g.pendingDisasters[i];
        if (g.week >= d.week) {
          g.pendingDisasters.splice(i, 1);
          g.cash -= d.cash;
          g.morale = clamp(g.morale - 6, 5, 100);
          g.doors.push(d.door);
          push({ t: d.text, sfx: 'alarm', major: true, fire: d.fire || 'OPS' });
        }
      }
    }

    // ---- money ----
    const growthDrive = g.speed * g.quality * Math.sqrt(g.coherence / 100);
    g.revW *= 1 + 0.010 * (growthDrive - 0.62);
    // Moonbeam compounds on every week your engine underperforms; they never
    // quite stand still, but a healthy Sunbeam keeps them small.
    g.rival = clamp(g.rival + Math.max(0.04, 0.9 * (1.0 - growthDrive)), 6, 100);
    const burnW = 8 + g.headcount * 0.95;
    g.cash += g.revW - burnW;

    // ---- scripted beats ----
    while (g.scriptIdx < g.script.length && g.script[g.scriptIdx].w <= g.week) {
      const s = g.script[g.scriptIdx++];
      if (s.cash) g.cash += s.cash;
      if (s.morale) g.morale = clamp(g.morale + s.morale, 2, 100);
      if (s.door && g.doors.indexOf(s.door) < 0) g.doors.push(s.door);
      push(Object.assign({ w: s.w }, s));
    }

    // ---- dynamic threshold beats ----
    for (const d of DYNAMIC) {
      if (d.modes && d.modes.indexOf(g.mode) < 0) continue;
      if (!g.firedIds[d.id] && d.when(g)) {
        g.firedIds[d.id] = true;
        push({ t: d.t, sfx: d.sfx, major: d.major });
      }
    }

    // ---- quarterly close ----
    if (g.week % 12 === 0) {
      const q = g.week / 12;
      const rev = Math.round(g.revW * 12);
      push({ t: 'Q' + q + ' closes. Revenue: $' + fmtK(rev) + '. Cash: $' + fmtK(Math.round(g.cash)) + '.', sfx: g.cash > 800 ? 'kaching' : 'thud', quarterly: true });
    }

    // ---- history + endings ----
    g.history.cash.push(Math.max(0, g.cash));
    g.history.morale.push(g.morale);
    g.history.health.push(health(g));

    if ((g.mode !== 'C' && g.mode !== 'F') || (g.mode === 'C' && g.cBad >= 3)) {
      const deadline = { A: 93, B: 105, D: 201, E: 201 }[g.mode] || 999;
      if (g.cash <= 0 || g.morale <= 3 || g.week >= deadline) {
        g.over = true;
        g.ended = g.mode === 'C' ? 'C_LOSE' : g.mode;
        const closer =
          g.mode === 'D' ? 'The papers are signed. Sunbeam is now a line item in Moonbeam\'s annual report. The final decision was, at last, entirely yours.' :
          g.mode === 'E' ? 'Sunbeam persists — pleasant, beloved, and permanently about to turn the corner. The corner sends its regards.' :
          'The lights go out — figuratively, then contractually.';
        push({ t: closer, sfx: 'trombone', major: true });
      }
    }
    if (g.mode === 'C' && !g.over) {
      if (g.cash <= 0) {
        g.over = true; g.ended = 'C_LOSE';
        push({ t: 'The lights go out — figuratively, then contractually.', sfx: 'trombone', major: true });
      } else if (g.week >= 144) {
        g.over = true;
        g.ended = g.cash > 400 ? 'C_WIN' : 'C_LOSE';
        push({ t: g.ended === 'C_WIN'
          ? 'Twelve quarters. Still here. Still boring. Still compounding.'
          : 'Twelve quarters, technically. The company survives you the way a ship survives a reef.', sfx: g.ended === 'C_WIN' ? 'ding' : 'trombone', major: true });
      }
    }
    if (g.mode === 'F' && !g.over) {
      if (g.cash <= 0 || g.morale <= 3) {
        g.over = true; g.ended = 'F_LOSE';
        push({ t: 'The company runs aground. The dial was set. The dial stayed set. The water moved.', sfx: 'trombone', major: true });
      } else if (g.week >= 192) {
        g.over = true;
        g.ended = (g.devSum / g.week) < 0.085 ? 'F_WIN' : 'F_DRIFT';
        push({ t: g.ended === 'F_WIN'
          ? 'Sixteen quarters of small corrections. From outside it looked like calm. It was steering.'
          : 'Sixteen quarters, survived. Now look at the chart — the dial you set, and the dial it needed.', sfx: g.ended === 'F_WIN' ? 'ding' : 'page', major: true });
      }
    }
    return ev;
  }

  // Apply a decision-card choice: 'take' | 'del' | 'memo' | 'auto'
  // (booleans accepted for back-compat: true=take). Returns
  // {text, good, sfx, reveal} — reveal carries the card's true stamps,
  // shown after the choice in Balance mode where stamps are hidden.
  function applyCard(g, card, choice) {
    if (typeof choice === 'boolean') choice = choice ? 'take' : 'del';
    const critical = card.oneWay && card.stakes === 'HIGH';
    const reveal = { stakes: card.stakes, oneWay: card.oneWay };
    if (g.mode !== 'C') {
      // forced-philosophy modes: consequences are already in the script.
      return { text: choice === 'take' ? card.take : card.del, good: null, sfx: choice === 'take' ? 'paper' : 'whoosh', reveal };
    }
    if (choice === 'auto') {
      // the successor arc: the org sorted it the way you would have
      g.cGood++; g.learning = clamp(g.learning + 4, 0, 100);
      return { text: card.take, good: true, sfx: 'ding', reveal };
    }
    if (choice === 'memo') {
      if (critical) {
        g.cGood++; g.learning = clamp(g.learning + 6, 0, 100);
        if (g.learning >= 46) {
          return { text: 'You ask for the two-pager. It arrives with a recommendation and a dissent attached. You add one sentence and sign. This is the machine, working.', good: true, sfx: 'ding', reveal };
        }
        g.cash -= 80;
        return { text: 'The memo misses a clause — it costs $80k and teaches a cohort more than a year of approvals would have. Tuition, but cheap tuition.', good: true, sfx: 'paper', reveal };
      }
      g.cMemoTax++; g.queue += 3;
      return { text: 'A reversible decision now has an executive summary, three appendices, and a billable hour. Process is also a tax.', good: false, sfx: 'thud', reveal };
    }
    if (choice === 'take' && critical) {
      g.cGood++; g.queue += 1;
      return { text: card.take, good: true, sfx: 'ding', reveal };
    }
    if (choice === 'del' && !critical) {
      g.cGood++; g.learning = clamp(g.learning + 4, 0, 100);
      return { text: card.del, good: true, sfx: 'ding', reveal };
    }
    if (choice === 'take') {
      g.cBad++; g.cHoard++; g.queue += 10;
      g.sanity = clamp(g.sanity - 6, 20, 100);
      return { text: card.take + ' (It was reversible. It did not need you — and now everything waits behind it.)', good: false, sfx: 'thud', reveal };
    }
    // delegated a one-way door: second-order bill arrives later
    g.cBad++; g.cGhost++;
    const d = card.disaster || { delay: 20, cash: 300, door: 'DOOR', text: 'FIRE: a one-way door you never looked at has slammed shut with the company inside.' };
    g.pendingDisasters.push(Object.assign({ week: g.week + d.delay }, d));
    return { text: card.del + ' (It was a one-way door. Nothing happens... yet.)', good: false, sfx: 'whoosh', reveal };
  }

  function fmtK(k) {
    return k >= 1000 ? (k / 1000).toFixed(1) + 'M' : Math.round(k) + 'k';
  }

  root.CEOSIM = {
    CARDS, ENDINGS, SORT_TEST, makeGame, tick, applyCard, health, fmtK, targetDial,
    CARD_WEEKS: { A: 10, B: 10, C: 8, D: 10, E: 10 }   // a card every N weeks; F has the dial instead
  };
})(typeof window !== 'undefined' ? window : globalThis);
