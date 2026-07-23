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
      del: 'Design ships it. Zero customers notice. The sun rises anyway.' }
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
    { w: 141, t: 'Someone asks what you actually do all day. You say "almost nothing, extremely carefully." It is the truth.', major: true }
  ];

  // Metric-threshold events: fired once when the predicate first turns true.
  const DYNAMIC = [
    { id: 'q15', when: g => g.queue >= 15, t: 'The decision queue crosses 15. Somewhere, a team starts a betting pool on verdict dates.', sfx: 'paper' },
    { id: 'q30', when: g => g.queue >= 30, t: 'Queue: 30. The pile on your desk is now visible in the building\'s structural survey.', sfx: 'paper', major: true },
    { id: 'q60', when: g => g.queue >= 60, t: 'Queue: 60. Items at the bottom have begun to fossilize. A paleontologist has been consulted.', sfx: 'thud', major: true },
    { id: 'm50', when: g => g.morale <= 50, t: 'Morale slips below 50. The kudos channel is now just the word "congrats" echoing.', morale: 0 },
    { id: 'm30', when: g => g.morale <= 30, t: 'Morale: 30. The exit-interview calendar has a waitlist.', sfx: 'thud', major: true },
    { id: 'c60', when: g => g.coherence <= 60, t: 'Coherence slips below 60%. Two roadmaps now cite each other as the competition.', sfx: 'paper' },
    { id: 'c35', when: g => g.coherence <= 35, t: 'Coherence: 35%. The org chart is best understood as a weather system.', sfx: 'thud', major: true },
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
      rnd: lcg(seed || 12345),
      week: 0,
      over: false,
      ended: null,          // set to an ENDINGS key when done
      cash: 2500,           // k$
      revW: 52,             // k$/week, grows or decays
      headcount: 40,
      queue: 0,
      morale: mode === 'B' ? 80 : 70,
      sanity: 100,
      coherence: 100,
      learning: 0,
      speed: 1,
      quality: 1,
      firedIds: {},         // dynamic events already fired
      scriptIdx: 0,
      keyMoments: [],       // {w, t} majors, for the front page
      doors: [],            // one-way doors walked through (mode B/C)
      pendingDisasters: [], // mode C: {week, ...disaster}
      cGood: 0, cBad: 0,    // mode C scorecard
      history: { cash: [], morale: [], health: [] }
    };
    g.script = mode === 'A' ? SCRIPT_A : mode === 'B' ? SCRIPT_B : SCRIPT_C;
    return g;
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

    if (g.mode !== 'C' || g.cBad >= 3) {
      const deadline = g.mode === 'A' ? 93 : g.mode === 'B' ? 105 : 999;
      if (g.cash <= 0 || g.morale <= 3 || g.week >= deadline) {
        g.over = true;
        g.ended = g.mode === 'C' ? 'C_LOSE' : g.mode;
        push({ t: 'The lights go out — figuratively, then contractually.', sfx: 'trombone', major: true });
      }
    }
    if (g.mode === 'C' && !g.over) {
      if (g.cash <= 0) {
        g.over = true; g.ended = 'C_LOSE';
        push({ t: 'The lights go out — figuratively, then contractually.', sfx: 'trombone', major: true });
      } else if (g.week >= 144) {
        g.over = true; g.ended = 'C_WIN';
        push({ t: 'Twelve quarters. Still here. Still boring. Still compounding.', sfx: 'ding', major: true });
      }
    }
    return ev;
  }

  // Apply a decision-card choice. keep=true means the CEO takes it.
  // Returns {text, good, sfx} for the UI.
  function applyCard(g, card, keep) {
    const critical = card.oneWay && card.stakes === 'HIGH';
    if (g.mode !== 'C') {
      // A/B: the choice is forced; consequences are already in the script.
      return { text: keep ? card.take : card.del, good: null, sfx: keep ? 'paper' : 'whoosh' };
    }
    if (keep && critical) {
      g.cGood++; g.queue += 1;
      return { text: card.take, good: true, sfx: 'ding' };
    }
    if (!keep && !critical) {
      g.cGood++; g.learning = clamp(g.learning + 4, 0, 100);
      return { text: card.del, good: true, sfx: 'ding' };
    }
    if (keep && !critical) {
      g.cBad++; g.queue += 10;
      g.sanity = clamp(g.sanity - 6, 20, 100);
      return { text: card.take + ' (Reversible. This did not need you — but now everything waits behind it.)', good: false, sfx: 'thud' };
    }
    // delegated a one-way door: second-order bill arrives later
    g.cBad++;
    const d = card.disaster || { delay: 20, cash: 300, door: 'DOOR', text: 'FIRE: a one-way door you never looked at has slammed shut with the company inside.' };
    g.pendingDisasters.push(Object.assign({ week: g.week + d.delay }, d));
    return { text: card.del + ' (Irreversible. Nothing happens... yet.)', good: false, sfx: 'whoosh' };
  }

  function fmtK(k) {
    return k >= 1000 ? (k / 1000).toFixed(1) + 'M' : Math.round(k) + 'k';
  }

  root.CEOSIM = {
    CARDS, ENDINGS, makeGame, tick, applyCard, health, fmtK,
    CARD_WEEKS: { A: 10, B: 10, C: 8 }   // a card every N weeks
  };
})(typeof window !== 'undefined' ? window : globalThis);
