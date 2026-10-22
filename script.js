// ============================================================
// PERSONAL FINANCE PORTAL - CLIENT APPLICATION SCRIPT
// ============================================================

let USER_ID = null;
let cats = [];
let accounts = [];
let transactions = [];
let currency = 'INR';
let users = [];

// Navigation configuration with Unicode icons
const pages = {
  dashboard: { eyebrow: 'OVERVIEW', title: 'Dashboard', icon: '📊' },
  transactions: { eyebrow: 'MONEY FLOW', title: 'Transactions', icon: '⇅' },
  accounts: { eyebrow: 'ACCOUNTS', title: 'Accounts & Cards', icon: '🏛' },
  budgets: { eyebrow: 'PLANNING', title: 'Category Budgets', icon: '📋' },
  goals: { eyebrow: 'GOALS', title: 'Savings Goals', icon: '🎯' },
  bills: { eyebrow: 'RECURRING', title: 'Bills & Subscriptions', icon: '🔄' },
  investments: { eyebrow: 'PORTFOLIO', title: 'Investments', icon: '📈' },
  networth: { eyebrow: 'FINANCIAL POSITION', title: 'Net Worth', icon: '⚖️' },
  reports: { eyebrow: 'ANALYTICS', title: 'Reports & Insights', icon: '📉' },
  assistant: { eyebrow: 'ASSISTANCE', title: 'AI Finance Assistant', icon: '💬' },
  settings: { eyebrow: 'ACCOUNT', title: 'Account Settings', icon: '⚙️' }
};

// Category palette fallback
const categoryColors = [
  '#0f766e', '#0284c7', '#7c3aed', '#db2777', '#ea580c',
  '#16a34a', '#ca8a04', '#475569', '#0891b2', '#4f46e5'
];

// Helper: Get element by ID
const $ = id => document.getElementById(id);

// Helper: Format monetary amounts with active user currency
const money = n => {
  const num = Number(n) || 0;
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency: currency || 'INR',
      maximumFractionDigits: 0
    }).format(num);
  } catch (e) {
    return `${currency} ${num.toLocaleString('en-IN')}`;
  }
};

// Helper: HTML escape
const esc = v => String(v ?? '').replace(/[&<>'"]/g, c => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  "'": '&#39;',
  '"': '&quot;'
}[c]));

// ============================================================
// TOAST NOTIFICATION SYSTEM
// ============================================================
function toast(message, type = 'success') {
  const container = $('toastContainer');
  if (!container) return;

  const icons = {
    success: '✓',
    error: '✕',
    warning: '⚠',
    info: 'ℹ'
  };

  const el = document.createElement('div');
  el.className = `toast toast-${type}`;
  el.innerHTML = `
    <span class="toast-icon">${icons[type] || 'ℹ'}</span>
    <div class="toast-content">${esc(message)}</div>
  `;

  container.appendChild(el);

  // Auto-dismiss after 3.8s
  const timer = setTimeout(() => dismissToast(el), 3800);
  el.onclick = () => {
    clearTimeout(timer);
    dismissToast(el);
  };
}

function dismissToast(el) {
  if (!el || el.classList.contains('toast-hide')) return;
  el.classList.add('toast-hide');
  setTimeout(() => el.remove(), 260);
}

// Confirmation helper replacing native confirm()
function confirmDialog(title, message, onConfirm) {
  openModal(
    title,
    'Please confirm your action',
    `<p style="font-size:14px; color:var(--text-secondary); line-height:1.6;">${esc(message)}</p>`,
    async () => {
      await onConfirm();
      closeModal();
    }
  );
  // Style the save button as danger
  const saveBtn = $('modalSave');
  if (saveBtn) {
    saveBtn.textContent = 'Confirm Delete';
    saveBtn.classList.remove('primary');
    saveBtn.classList.add('danger-btn');
  }
}

// ============================================================
// API CLIENT
// ============================================================
async function api(url, options = {}) {
  try {
    const res = await fetch(url, options);
    let data = {};
    try {
      data = await res.json();
    } catch (_) {}

    if (!res.ok) {
      throw new Error(data.error || `HTTP error ${res.status}`);
    }
    return data;
  } catch (err) {
    console.error('API Error:', err);
    throw err;
  }
}

// ============================================================
// NAVIGATION & SIDEBAR
// ============================================================
function navInit() {
  const nav = $('nav');
  if (!nav) return;

  nav.innerHTML = Object.keys(pages)
    .filter(p => p !== 'settings')
    .map(p => `
      <button class="nav-item ${p === 'dashboard' ? 'active' : ''}" data-page="${p}" onclick="showPage('${p}')">
        <span class="nav-icon">${pages[p].icon}</span>
        <span>${pages[p].title}</span>
      </button>
    `)
    .join('');
}

async function showPage(pageId) {
  if (!pages[pageId]) return;

  // Close mobile sidebar if open
  closeSidebar();

  // Switch active page
  document.querySelectorAll('.page').forEach(el => el.classList.remove('active'));
  const targetPage = $(`page-${pageId}`);
  if (targetPage) targetPage.classList.add('active');

  // Update nav active states
  document.querySelectorAll('.nav-item').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.page === pageId);
  });

  // Update topbar headers
  $('eyebrow').textContent = pages[pageId].eyebrow;
  $('pageTitle').textContent = pages[pageId].title;
  window.scrollTo({ top: 0, behavior: 'smooth' });

  // Load relevant page data
  const loaders = {
    dashboard: loadDashboard,
    transactions: loadTransactions,
    accounts: loadAccounts,
    budgets: loadBudgets,
    goals: loadGoals,
    bills: loadBills,
    investments: loadInvestments,
    networth: loadNetworth,
    reports: loadReports
  };

  if (loaders[pageId]) {
    try {
      await loaders[pageId]();
    } catch (err) {
      toast(err.message, 'error');
    }
  }
}

function toggleSidebar() {
  const sidebar = $('sidebar');
  const overlay = $('sidebarOverlay');
  if (sidebar && overlay) {
    sidebar.classList.toggle('open');
    overlay.classList.toggle('active');
  }
}

function closeSidebar() {
  const sidebar = $('sidebar');
  const overlay = $('sidebarOverlay');
  if (sidebar && overlay) {
    sidebar.classList.remove('open');
    overlay.classList.remove('active');
  }
}

// ============================================================
// USER & SETTINGS MANAGEMENT
// ============================================================
function applyTheme(theme) {
  const isDark = String(theme).toLowerCase() === 'dark';
  document.body.classList.toggle('dark-theme', isDark);
}

async function loadUser() {
  const user = await api(`/api/user/${USER_ID}`);
  currency = user.default_currency || 'INR';

  // Update display names
  $('sideName').textContent = user.full_name || 'User';
  $('welcomeMessage').textContent = `Welcome back, ${user.full_name || 'User'}`;

  // Avatar initials
  const initials = (user.full_name || '?')
    .split(' ')
    .filter(Boolean)
    .map(x => x[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
  $('avatar').textContent = initials;
  $('profile').textContent = initials;

  // Settings form values
  $('settingName').value = user.full_name || '';
  $('settingEmail').value = user.email || '';
  $('settingCurrency').value = currency;
  $('settingTheme').value = (user.theme || 'Light').toLowerCase() === 'dark' ? 'Dark' : 'Light';

  applyTheme(user.theme || 'Light');

  // Default dashboard period picker
  const now = new Date();
  const currentMonthStr = now.toISOString().slice(0, 7);
  if (!$('dashMonthPicker').value) {
    $('dashMonthPicker').value = currentMonthStr;
  }
  if ($('budgetMonthFilter') && !$('budgetMonthFilter').value) {
    $('budgetMonthFilter').value = currentMonthStr;
  }

  $('dashboardPeriod').textContent = now.toLocaleDateString('en-US', {
    month: 'long',
    year: 'numeric'
  });
}

async function setupUserSelector() {
  users = await api('/api/users');
  const selector = $('userSelector');

  selector.innerHTML = users.map(u => `
    <option value="${u.user_id}">
      ${esc(u.full_name)} (${esc(u.default_currency)})
    </option>
  `).join('');

  selector.value = String(USER_ID);

  selector.onchange = async () => {
    USER_ID = Number(selector.value);
    await loadUser();
    await loadFormData();

    const activePage = document.querySelector('.page.active')?.id?.replace('page-', '') || 'dashboard';
    await showPage(activePage);
    toast(`Switched account to ${$('sideName').textContent}`, 'info');
  };
}

function openUserModal() {
  openModal(
    'Create New Account Holder',
    'Add an independent user profile with their own accounts and data.',
    `
      <label>Full Name
        <input id="uName" placeholder="e.g. Sarah Connor" required>
      </label>
      <label>Email Address
        <input id="uEmail" type="email" placeholder="e.g. sarah@example.com" required>
      </label>
      <label>Default Currency
        <select id="uCurrency">
          <option value="INR">Indian Rupee (₹ INR)</option>
          <option value="USD">US Dollar ($ USD)</option>
          <option value="EUR">Euro (€ EUR)</option>
          <option value="GBP">British Pound (£ GBP)</option>
        </select>
      </label>
    `,
    async () => {
      const name = $('uName').value.trim();
      const email = $('uEmail').value.trim();
      const curr = $('uCurrency').value;

      if (!name || !email) {
        toast('Please enter name and email.', 'warning');
        return;
      }

      const newUser = await api('/api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ full_name: name, email, default_currency: curr })
      });

      closeModal();
      toast(`User ${newUser.full_name} created successfully!`, 'success');

      USER_ID = Number(newUser.user_id);
      await setupUserSelector();
      await loadUser();
      await loadFormData();
      await showPage('dashboard');
    }
  );
}

async function saveSettings() {
  try {
    const name = $('settingName').value.trim();
    const email = $('settingEmail').value.trim();
    const currencyValue = $('settingCurrency').value;
    const themeValue = $('settingTheme').value;

    if (!name || !email) {
      toast('Please enter your name and email.', 'warning');
      return;
    }

    const updatedUser = await api(`/api/user/${USER_ID}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        full_name: name,
        email,
        default_currency: currencyValue,
        theme: themeValue
      })
    });

    // Update state from returned user object
    currency = updatedUser.default_currency || currencyValue;
    applyTheme(updatedUser.theme || themeValue);

    await setupUserSelector();
    await loadUser();
    await loadFormData();

    toast('Settings saved successfully.', 'success');
  } catch (err) {
    toast(err.message, 'error');
  }
}

// ============================================================
// FORM DATA (CATEGORIES & ACCOUNTS)
// ============================================================
async function loadFormData() {
  [cats, accounts] = await Promise.all([
    api('/api/categories'),
    api(`/api/accounts?user_id=${USER_ID}`)
  ]);

  const catOptions = '<option value="All">All Categories</option>' +
    cats.map(c => `<option value="${esc(c.category_name)}">${esc(c.category_name)}</option>`).join('');

  if ($('filterCategory')) {
    $('filterCategory').innerHTML = catOptions;
  }
}

// ============================================================
// DASHBOARD MODULE
// ============================================================
async function onDashboardMonthChange(selectedMonth) {
  await loadDashboard(selectedMonth);
}

async function loadDashboard(monthOverride = null) {
  const month = monthOverride || $('dashMonthPicker')?.value || new Date().toISOString().slice(0, 7);
  const monthParam = month ? `&month=${month}` : '';

  const [dash, monthlyData, catData, recentTx, budgetList] = await Promise.all([
    api(`/api/dashboard?user_id=${USER_ID}${monthParam}`),
    api(`/api/dashboard/monthly?user_id=${USER_ID}`),
    api(`/api/dashboard/categories?user_id=${USER_ID}${monthParam}`),
    api(`/api/transactions?user_id=${USER_ID}`),
    api(`/api/budgets?user_id=${USER_ID}${monthParam}`)
  ]);

  // Update Stat Cards
  $('dashBalance').textContent = money(dash.balance);
  $('dashIncome').textContent = money(dash.income);
  $('dashExpenses').textContent = money(dash.expenses);
  $('dashSavings').textContent = money(dash.savings);
  $('dashSavingsRate').textContent = `${dash.savingsRate}% of income saved`;

  // Render Charts
  renderBarChart(monthlyData);
  renderDonutChart(catData);

  // Render Recent Transactions (Top 5)
  const recentTable = $('dashboardTransactions');
  recentTable.innerHTML = recentTx.slice(0, 5).map(r => `
    <tr>
      <td><b>${esc(r.description || r.category_name || 'Transaction')}</b></td>
      <td>
        <span class="badge badge-category">
          <span class="badge-cat-dot" style="background:${r.category_color || 'var(--primary)'}"></span>
          ${esc(r.category_name || 'General')}
        </span>
      </td>
      <td>${esc(r.formatted_date)}</td>
      <td class="${r.transaction_type === 'Income' ? 'positive' : 'negative'}">
        ${r.transaction_type === 'Income' ? '+' : '−'}${money(r.amount)}
      </td>
    </tr>
  `).join('') || '<tr><td colspan="4" class="empty">No recent transactions recorded.</td></tr>';

  // Render Dashboard Budget Status
  const activeBudgets = budgetList.filter(b => b.month_year === month);
  const totalBudget = activeBudgets.reduce((s, b) => s + Number(b.amount), 0);
  const totalSpent = activeBudgets.reduce((s, b) => s + Number(b.spent), 0);
  const pctUsed = totalBudget ? Math.round((totalSpent / totalBudget) * 100) : 0;

  const budgetContainer = $('dashboardBudget');
  if (activeBudgets.length > 0) {
    const statusClass = pctUsed > 100 ? 'danger' : pctUsed >= 75 ? 'warning' : 'safe';
    budgetContainer.innerHTML = `
      <div class="budget-row">
        <div class="budget-header">
          <div>
            <b>${money(totalSpent)}</b> spent of <b>${money(totalBudget)}</b> allocated
          </div>
          <strong class="${statusClass === 'danger' ? 'negative' : ''}">${pctUsed}% used</strong>
        </div>
        <div class="progress">
          <i class="${statusClass}" style="width: ${Math.min(100, pctUsed)}%;"></i>
        </div>
        <div class="budget-footer">
          <span>Active Budgets: ${activeBudgets.length}</span>
          <span>${money(Math.max(0, totalBudget - totalSpent))} remaining</span>
        </div>
      </div>
    `;
  } else {
    budgetContainer.innerHTML = '<div class="empty">No category budgets created for this month.</div>';
  }
}

function renderBarChart(rows) {
  const chart = $('incomeExpenseChart');
  if (!rows || !rows.length) {
    chart.innerHTML = '<div class="empty">No financial cash flow data recorded yet.</div>';
    return;
  }

  const maxVal = Math.max(...rows.flatMap(r => [Number(r.income), Number(r.expenses)]), 1);

  chart.innerHTML = rows.map(r => {
    const incHeight = Math.max(3, Math.round((Number(r.income) / maxVal) * 85));
    const expHeight = Math.max(3, Math.round((Number(r.expenses) / maxVal) * 85));

    return `
      <div class="bar-group">
        <div class="bar-bars">
          <div class="bar income" style="height:${incHeight}%" data-tooltip="Income: ${money(r.income)}"></div>
          <div class="bar expense" style="height:${expHeight}%" data-tooltip="Expenses: ${money(r.expenses)}"></div>
        </div>
        <span class="bar-label">${esc(r.month_label || r.month.slice(5))}</span>
      </div>
    `;
  }).join('');
}

function renderDonutChart(rows) {
  const svg = $('donutSvg');
  const catList = $('dashboardCategories');
  const total = rows.reduce((s, r) => s + Number(r.total), 0);

  $('categoryTotal').textContent = money(total);

  if (!rows || !rows.length || total <= 0) {
    svg.innerHTML = '<circle cx="21" cy="21" r="15.91549430918954" fill="transparent" stroke="var(--border-color)" stroke-width="6"></circle>';
    catList.innerHTML = '<div class="empty">No expenses recorded for this month.</div>';
    return;
  }

  let accumulatedPercent = 0;
  const circumference = 100; // using r = 15.91549430918954 gives 2 * PI * r = 100

  const circles = rows.map((r, i) => {
    const pct = (Number(r.total) / total) * 100;
    const strokeDasharray = `${pct} ${circumference - pct}`;
    const strokeDashoffset = -accumulatedPercent;
    accumulatedPercent += pct;
    const color = r.color || categoryColors[i % categoryColors.length];

    return `
      <circle cx="21" cy="21" r="15.91549430918954"
              fill="transparent"
              stroke="${color}"
              stroke-width="6"
              stroke-dasharray="${strokeDasharray}"
              stroke-dashoffset="${strokeDashoffset}"></circle>
    `;
  }).join('');

  svg.innerHTML = circles;

  catList.innerHTML = rows.map((r, i) => {
    const pct = Math.round((Number(r.total) / total) * 100);
    const color = r.color || categoryColors[i % categoryColors.length];
    return `
      <div class="category-item">
        <div class="cat-left">
          <span class="cat-dot" style="background:${color};"></span>
          <span>${r.icon ? r.icon + ' ' : ''}${esc(r.category_name)}</span>
        </div>
        <div>
          <b>${money(r.total)}</b>
          <span style="font-size:11px; color:var(--text-secondary); margin-left:4px;">(${pct}%)</span>
        </div>
      </div>
    `;
  }).join('');
}

// ============================================================
// TRANSACTIONS MODULE (VIEW, ADD, EDIT, DELETE, FILTER)
// ============================================================
async function loadTransactions() {
  transactions = await api(`/api/transactions?user_id=${USER_ID}`);
  renderTransactions(transactions);
}

function renderTransactions(rows) {
  const tbody = $('transactionRows');
  if (!rows || !rows.length) {
    tbody.innerHTML = '<tr><td colspan="8" class="empty">No transactions match your criteria.</td></tr>';
    return;
  }

  tbody.innerHTML = rows.map(r => `
    <tr>
      <td><b>${esc(r.description || r.category_name || 'Transaction')}</b></td>
      <td>
        <span class="badge ${r.transaction_type === 'Income' ? 'badge-income' : 'badge-expense'}">
          ${r.transaction_type}
        </span>
      </td>
      <td>
        <span class="badge badge-category">
          <span class="badge-cat-dot" style="background:${r.category_color || 'var(--primary)'}"></span>
          ${r.category_icon ? r.category_icon + ' ' : ''}${esc(r.category_name || 'Uncategorized')}
        </span>
      </td>
      <td>${esc(r.formatted_date)}</td>
      <td>${esc(r.account_name || '-')}</td>
      <td><span class="badge badge-method">${esc(r.payment_method || 'Cash')}</span></td>
      <td class="${r.transaction_type === 'Income' ? 'positive' : 'negative'}">
        ${r.transaction_type === 'Income' ? '+' : '−'}${money(r.amount)}
      </td>
      <td style="text-align:right;">
        <button class="secondary btn-sm" onclick="editTransaction(${r.transaction_id})">Edit</button>
        <button class="danger-btn btn-sm" onclick="deleteTransaction(${r.transaction_id})">Delete</button>
      </td>
    </tr>
  `).join('');
}

function applyFilters() {
  const query = $('searchTransactions').value.toLowerCase().trim();
  const type = $('filterType').value;
  const cat = $('filterCategory').value;
  const method = $('filterPaymentMethod').value;
  const startDate = $('filterStartDate').value;
  const endDate = $('filterEndDate').value;
  const minAmt = parseFloat($('filterMinAmount').value);
  const maxAmt = parseFloat($('filterMaxAmount').value);

  const filtered = transactions.filter(r => {
    // Text search
    if (query) {
      const haystack = `${r.description || ''} ${r.category_name || ''} ${r.account_name || ''} ${r.payment_method || ''}`.toLowerCase();
      if (!haystack.includes(query)) return false;
    }
    // Type
    if (type !== 'All' && r.transaction_type !== type) return false;
    // Category
    if (cat !== 'All' && r.category_name !== cat) return false;
    // Payment method
    if (method !== 'All' && r.payment_method !== method) return false;
    // Date range
    if (startDate && r.raw_date < startDate) return false;
    if (endDate && r.raw_date > endDate) return false;
    // Amount range
    if (!isNaN(minAmt) && Number(r.amount) < minAmt) return false;
    if (!isNaN(maxAmt) && Number(r.amount) > maxAmt) return false;

    return true;
  });

  renderTransactions(filtered);
}

function clearFilters() {
  $('searchTransactions').value = '';
  $('filterType').value = 'All';
  $('filterCategory').value = 'All';
  $('filterPaymentMethod').value = 'All';
  $('filterStartDate').value = '';
  $('filterEndDate').value = '';
  $('filterMinAmount').value = '';
  $('filterMaxAmount').value = '';
  renderTransactions(transactions);
}

function openTransactionModal(tx = null) {
  const isEdit = Boolean(tx);
  openModal(
    isEdit ? 'Edit Transaction' : 'Record Transaction',
    isEdit ? 'Update transaction details and adjust balances' : 'Add income or an expense to an account',
    `
      <label>Transaction Type
        <select id="fType">
          <option value="Expense" ${tx?.transaction_type === 'Expense' ? 'selected' : ''}>Expense (−)</option>
          <option value="Income" ${tx?.transaction_type === 'Income' ? 'selected' : ''}>Income (+)</option>
        </select>
      </label>

      <label>Amount (${currency})
        <input id="fAmount" type="number" min="0.01" step="0.01" placeholder="0.00" value="${tx?.amount || ''}" required>
      </label>

      <label>Category
        <select id="fCategory"></select>
      </label>

      <label>Account
        <select id="fAccount"></select>
      </label>

      <label>Payment Method
        <select id="fMethod">
          <option value="Cash" ${tx?.payment_method === 'Cash' ? 'selected' : ''}>Cash</option>
          <option value="UPI" ${tx?.payment_method === 'UPI' ? 'selected' : ''}>UPI</option>
          <option value="Card" ${tx?.payment_method === 'Card' ? 'selected' : ''}>Debit/Credit Card</option>
          <option value="Bank Transfer" ${tx?.payment_method === 'Bank Transfer' ? 'selected' : ''}>Bank Transfer</option>
          <option value="Net Banking" ${tx?.payment_method === 'Net Banking' ? 'selected' : ''}>Net Banking</option>
          <option value="Other" ${tx?.payment_method === 'Other' ? 'selected' : ''}>Other</option>
        </select>
      </label>

      <label>Description / Note
        <input id="fDescription" placeholder="e.g. Grocery shopping, Freelance invoice" value="${esc(tx?.description || '')}">
      </label>

      <label>Date
        <input id="fDate" type="date" value="${tx?.raw_date || new Date().toISOString().slice(0, 10)}" required>
      </label>
    `,
    async () => {
      const amount = Number($('fAmount').value);
      const accountId = Number($('fAccount').value);
      const categoryId = Number($('fCategory').value);
      const type = $('fType').value;
      const method = $('fMethod').value;
      const desc = $('fDescription').value.trim();
      const date = $('fDate').value;

      if (!accountId || !categoryId || !amount || amount <= 0 || !date) {
        toast('Please enter valid amount, account, category, and date.', 'warning');
        return;
      }

      const payload = {
        user_id: USER_ID,
        account_id: accountId,
        category_id: categoryId,
        amount,
        transaction_type: type,
        payment_method: method,
        description: desc,
        transaction_date: date
      };

      if (isEdit) {
        await api(`/api/transactions/${tx.transaction_id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        toast('Transaction updated successfully.', 'success');
      } else {
        await api('/api/transactions', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        toast('Transaction recorded successfully.', 'success');
      }

      closeModal();
      await Promise.all([loadTransactions(), loadDashboard(), loadAccounts()]);
    }
  );

  // Populate dynamic category and account dropdowns
  const updateDropdowns = () => {
    const currentType = $('fType').value;
    const filteredCats = cats.filter(c => c.category_type === currentType);
    $('fCategory').innerHTML = filteredCats.map(c => `
      <option value="${c.category_id}" ${tx?.category_id === c.category_id ? 'selected' : ''}>
        ${c.icon ? c.icon + ' ' : ''}${esc(c.category_name)}
      </option>
    `).join('');

    $('fAccount').innerHTML = accounts.map(a => `
      <option value="${a.account_id}" ${tx?.account_id === a.account_id ? 'selected' : ''}>
        ${esc(a.account_name)} (${money(a.balance)})
      </option>
    `).join('') || '<option value="">No accounts available</option>';
  };

  $('fType').onchange = updateDropdowns;
  updateDropdowns();
}

let importPreviewData = [];

async function openImportModal() {
  openModal(
    'Import Bank Statement',
    'Upload CSV, Excel or PDF bank statements and review detected transactions before importing.',
    `
      <label>Statement File
        <div class="import-dropzone">
          <input id="importFile" type="file" accept=".csv,.xlsx,.xls,.pdf">
        </div>
      </label>
      <div id="importPreview" class="import-preview">
        <div class="empty">No statement selected yet. Choose a CSV, Excel, or PDF file.</div>
      </div>
    `,
    async () => {
      const selected = [];
      const rows = document.querySelectorAll('#importPreview .import-row');
      rows.forEach(row => {
        const idx = Number(row.dataset.index);
        const checkbox = row.querySelector('input[type="checkbox"]');
        if (!checkbox || checkbox.disabled || !checkbox.checked) return;
        const tx = importPreviewData[idx];
        if (!tx) return;

        const categoryEl = row.querySelector('[data-role="category"]');
        const accountEl = row.querySelector('[data-role="account"]');
        const methodEl = row.querySelector('[data-role="method"]');

        if (!categoryEl || !accountEl || !methodEl) {
          toast('This statement row is missing required fields. Please check account setup or refresh the preview.', 'warning');
          return;
        }

        const categoryId = Number(categoryEl.value);
        const accountId = Number(accountEl.value);
        const paymentMethod = methodEl.value;

        if (!Number.isFinite(categoryId) || categoryId <= 0 || !Number.isFinite(accountId) || accountId <= 0) {
          toast('Please assign a valid category and account for each selected transaction.', 'warning');
          return;
        }

        selected.push({
          ...tx,
          category_id: categoryId,
          account_id: accountId,
          paymentMethod: paymentMethod || tx.paymentMethod || 'Bank Transfer'
        });
      });

      if (!selected.length) {
        toast('Select at least one transaction to import.', 'warning');
        return;
      }

      const res = await fetch(`/api/import/confirm?user_id=${USER_ID}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ transactions: selected })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(data.error || 'Could not import transactions.');

      closeModal();
      toast(data.message || 'Transactions imported successfully.', 'success');
      await Promise.all([loadTransactions(), loadDashboard(), loadAccounts(), loadFormData()]);
    }
  );

  const saveBtn = $('modalSave');
  if (saveBtn) {
    saveBtn.textContent = 'Import Selected';
    saveBtn.disabled = true;
  }

  const fileInput = $('importFile');
  if (fileInput) {
    fileInput.onchange = async () => {
      const file = fileInput.files && fileInput.files[0];
      if (!file) return;
      const preview = $('importPreview');
      preview.innerHTML = '<div class="empty">Uploading and scanning statement...</div>';
      const formData = new FormData();
      formData.append('statement', file);
      formData.append('user_id', String(USER_ID));

      try {
        const res = await fetch(`/api/import/upload?user_id=${USER_ID}`, {
          method: 'POST',
          body: formData
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) throw new Error(data.error || 'Could not parse the statement.');

        importPreviewData = data.transactions || [];
        const accountOptions = (data.accounts || []).map(acc => `
          <option value="${acc.account_id}" ${Number(data.defaultAccountId) === Number(acc.account_id) ? 'selected' : ''}>
            ${esc(acc.account_name)}
          </option>
        `).join('');
        const categoryOptions = (data.categories || []).map(cat => `
          <option value="${cat.category_id}">${cat.category_name}</option>
        `).join('');

        if (!importPreviewData.length) {
          preview.innerHTML = '<div class="empty">No valid transactions were found in this file.</div>';
          saveBtn.disabled = true;
          return;
        }

        if (!data.accounts || !data.accounts.length) {
          preview.innerHTML = '<div class="empty">No account available. Add an account before importing this statement.</div>';
          saveBtn.disabled = true;
          return;
        }

        preview.innerHTML = importPreviewData.map((tx, index) => {
          const accountSelect = data.accounts && data.accounts.length
            ? `<select data-role="account" data-index="${index}" ${tx.isDuplicate ? 'disabled' : ''}>${accountOptions}</select>`
            : '<span class="empty">No account available</span>';
          const categorySelect = `<select data-role="category" data-index="${index}" ${tx.isDuplicate ? 'disabled' : ''}>${(data.categories || []).filter(cat => cat.category_type === tx.type).map(cat => `<option value="${cat.category_id}" ${Number(tx.category_id) === Number(cat.category_id) ? 'selected' : ''}>${esc(cat.category_name)}</option>`).join('')}</select>`;
          const methodSelect = `<select data-role="method" data-index="${index}" ${tx.isDuplicate ? 'disabled' : ''}>
            <option value="Cash" ${tx.paymentMethod === 'Cash' ? 'selected' : ''}>Cash</option>
            <option value="UPI" ${tx.paymentMethod === 'UPI' ? 'selected' : ''}>UPI</option>
            <option value="Card" ${tx.paymentMethod === 'Card' ? 'selected' : ''}>Card</option>
            <option value="Bank Transfer" ${tx.paymentMethod === 'Bank Transfer' || !tx.paymentMethod ? 'selected' : ''}>Bank Transfer</option>
            <option value="Net Banking" ${tx.paymentMethod === 'Net Banking' ? 'selected' : ''}>Net Banking</option>
            <option value="Other" ${tx.paymentMethod === 'Other' ? 'selected' : ''}>Other</option>
          </select>`;

          return `
            <div class="import-row ${tx.isDuplicate ? 'duplicate' : ''}" data-index="${index}">
              <input type="checkbox" ${tx.import && !tx.isDuplicate ? 'checked' : ''} ${tx.isDuplicate ? 'disabled' : ''}>
              <div>
                <strong>${esc(tx.description || 'Unnamed transaction')}</strong>
                <small>${esc(tx.date)} • ${esc(tx.type)} • ${money(tx.amount)}</small>
              </div>
              ${categorySelect}
              ${accountSelect}
              ${methodSelect}
            </div>
          `;
        }).join('');

        const selectable = document.querySelectorAll('#importPreview .import-row input[type="checkbox"]');
        saveBtn.disabled = selectable.length === 0 || [...selectable].every(input => input.disabled);
      } catch (err) {
        preview.innerHTML = `<div class="empty">${esc(err.message)}</div>`;
        saveBtn.disabled = true;
      }
    };
  }
}

function editTransaction(id) {
  const tx = transactions.find(t => t.transaction_id === id);
  if (tx) openTransactionModal(tx);
}

function deleteTransaction(id) {
  confirmDialog(
    'Delete Transaction',
    'Are you sure you want to delete this transaction? The corresponding account balance will be automatically adjusted.',
    async () => {
      await api(`/api/transactions/${id}?user_id=${USER_ID}`, { method: 'DELETE' });
      toast('Transaction deleted successfully.', 'success');
      await Promise.all([loadTransactions(), loadDashboard(), loadAccounts()]);
    }
  );
}

// ============================================================
// ACCOUNTS MODULE
// ============================================================
async function loadAccounts() {
  accounts = await api(`/api/accounts?user_id=${USER_ID}`);
  const grid = $('accountsGrid');

  if (!accounts || !accounts.length) {
    grid.innerHTML = '<div class="empty">No accounts configured yet. Click "+ Add Account" to begin.</div>';
    return;
  }

  grid.innerHTML = accounts.map(a => `
    <div class="account-card">
      <div>
        <div class="account-card-header">
          <span class="account-type-badge">${esc(a.account_type)}</span>
          <span style="font-size:20px;">🏦</span>
        </div>
        <h3>${esc(a.account_name)}</h3>
        <div class="account-balance">${money(a.balance)}</div>
      </div>
      <div class="card-actions">
        <button class="secondary btn-sm" onclick="editAccount(${a.account_id})">Edit</button>
        <button class="danger-btn btn-sm" onclick="deleteAccount(${a.account_id})">Delete</button>
      </div>
    </div>
  `).join('');
}

function openAccountModal(account = null) {
  const isEdit = Boolean(account);
  openModal(
    isEdit ? 'Edit Account' : 'Add Financial Account',
    'Manage your checking, savings, card or cash accounts',
    `
      <label>Account Name
        <input id="aName" value="${esc(account?.account_name || '')}" placeholder="e.g. HDFC Savings, Cash Wallet" required>
      </label>

      <label>Account Type
        <select id="aType">
          <option value="Savings" ${account?.account_type === 'Savings' ? 'selected' : ''}>Savings Account</option>
          <option value="Checking" ${account?.account_type === 'Checking' ? 'selected' : ''}>Checking Account</option>
          <option value="Credit Card" ${account?.account_type === 'Credit Card' ? 'selected' : ''}>Credit Card</option>
          <option value="UPI / Digital" ${account?.account_type === 'UPI / Digital' ? 'selected' : ''}>UPI / Digital Wallet</option>
          <option value="Cash" ${account?.account_type === 'Cash' ? 'selected' : ''}>Cash</option>
          <option value="Other" ${account?.account_type === 'Other' ? 'selected' : ''}>Other</option>
        </select>
      </label>

      <label>Current Balance (${currency})
        <input id="aBalance" type="number" step="0.01" value="${account?.balance ?? 0}" required>
      </label>
    `,
    async () => {
      const name = $('aName').value.trim();
      const type = $('aType').value;
      const balance = Number($('aBalance').value);

      if (!name || !type || !Number.isFinite(balance)) {
        toast('Please enter a valid account name, type and balance.', 'warning');
        return;
      }

      const body = { user_id: USER_ID, account_name: name, account_type: type, balance };

      if (isEdit) {
        await api(`/api/accounts/${account.account_id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Account updated successfully.', 'success');
      } else {
        await api('/api/accounts', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Account created successfully.', 'success');
      }

      closeModal();
      await Promise.all([loadAccounts(), loadFormData(), loadDashboard()]);
    }
  );
}

function editAccount(id) {
  const acc = accounts.find(a => a.account_id === id);
  if (acc) openAccountModal(acc);
}

function deleteAccount(id) {
  confirmDialog(
    'Delete Account',
    'Are you sure you want to delete this account? Accounts with recorded transactions cannot be deleted.',
    async () => {
      await api(`/api/accounts/${id}?user_id=${USER_ID}`, { method: 'DELETE' });
      toast('Account deleted successfully.', 'success');
      await Promise.all([loadAccounts(), loadFormData(), loadDashboard()]);
    }
  );
}

// ============================================================
// BUDGETS MODULE
// ============================================================
async function loadBudgets() {
  const month = $('budgetMonthFilter')?.value || new Date().toISOString().slice(0, 7);
  $('budgetMonthLabel').textContent = new Date(month + '-01').toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

  const rows = await api(`/api/budgets?user_id=${USER_ID}&month=${month}`);

  const total = rows.reduce((s, x) => s + Number(x.amount), 0);
  const spent = rows.reduce((s, x) => s + Number(x.spent), 0);
  const remaining = Math.max(0, total - spent);
  const usedPct = total > 0 ? Math.round((spent / total) * 100) : 0;

  $('budgetTotal').textContent = money(total);
  $('budgetSpent').textContent = money(spent);
  $('budgetRemaining').textContent = money(remaining);
  $('budgetUsed').textContent = `${usedPct}% used`;

  const list = $('budgetsList');
  if (!rows || !rows.length) {
    list.innerHTML = '<div class="empty">No category budgets created for this month. Click "+ Create Budget" to set one up.</div>';
    return;
  }

  list.innerHTML = rows.map(b => {
    const pct = b.amount > 0 ? Math.round((Number(b.spent) / Number(b.amount)) * 100) : 0;
    const statusClass = pct > 100 ? 'danger' : pct >= 75 ? 'warning' : 'safe';

    return `
      <div class="budget-row">
        <div class="budget-header">
          <div class="budget-cat-name">
            <span class="badge-cat-dot" style="background:${b.category_color || 'var(--primary)'};"></span>
            <span>${b.category_icon ? b.category_icon + ' ' : ''}${esc(b.category_name)}</span>
          </div>
          <div class="budget-meta">
            <b>${money(b.spent)}</b> spent of <b>${money(b.amount)}</b>
          </div>
        </div>

        <div class="progress">
          <i class="${statusClass}" style="width:${Math.min(100, pct)}%;"></i>
        </div>

        <div class="budget-footer">
          <span class="${pct > 100 ? 'negative' : ''}">
            ${pct}% used ${pct > 100 ? '• OVER BUDGET' : ''}
          </span>
          <div>
            <button class="secondary btn-sm" onclick="editBudget(${b.budget_id}, ${b.category_id}, ${b.amount}, '${b.month_year}')">Edit</button>
            <button class="danger-btn btn-sm" onclick="deleteBudget(${b.budget_id})">Delete</button>
          </div>
        </div>
      </div>
    `;
  }).join('');
}

function openBudgetModal(bId = null, catId = null, amt = null, mYear = null) {
  const currentMonth = $('budgetMonthFilter')?.value || new Date().toISOString().slice(0, 7);

  openModal(
    bId ? 'Edit Category Budget' : 'Create Category Budget',
    'Set a spending limit for an expense category in a given month',
    `
      <label>Expense Category
        <select id="bCat">
          ${cats.filter(c => c.category_type === 'Expense').map(c => `
            <option value="${c.category_id}" ${catId === c.category_id ? 'selected' : ''}>
              ${c.icon ? c.icon + ' ' : ''}${esc(c.category_name)}
            </option>
          `).join('')}
        </select>
      </label>

      <label>Monthly Budget Limit (${currency})
        <input id="bAmount" type="number" min="1" step="1" value="${amt || ''}" placeholder="e.g. 5000" required>
      </label>

      <label>Month
        <input id="bMonth" type="month" value="${mYear || currentMonth}" required>
      </label>
    `,
    async () => {
      const categoryId = Number($('bCat').value);
      const amount = Number($('bAmount').value);
      const monthYear = $('bMonth').value;

      if (!categoryId || !amount || amount <= 0 || !monthYear) {
        toast('Please enter valid category, budget limit, and month.', 'warning');
        return;
      }

      if (bId) {
        await api(`/api/budgets/${bId}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ user_id: USER_ID, category_id: categoryId, amount, month_year: monthYear })
        });
        toast('Budget updated successfully.', 'success');
      } else {
        await api('/api/budgets', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ user_id: USER_ID, category_id: categoryId, amount, month_year: monthYear })
        });
        toast('Budget saved successfully.', 'success');
      }

      closeModal();
      await Promise.all([loadBudgets(), loadDashboard()]);
    }
  );
}

function editBudget(id, categoryId, amount, monthYear) {
  openBudgetModal(id, categoryId, amount, monthYear);
}

function deleteBudget(id) {
  confirmDialog(
    'Delete Budget',
    'Are you sure you want to remove this budget allocation?',
    async () => {
      await api(`/api/budgets/${id}?user_id=${USER_ID}`, { method: 'DELETE' });
      toast('Budget deleted successfully.', 'success');
      await Promise.all([loadBudgets(), loadDashboard()]);
    }
  );
}

// ============================================================
// SAVINGS GOALS MODULE
// ============================================================
let savedGoalsCache = [];

async function loadGoals() {
  savedGoalsCache = await api(`/api/goals?user_id=${USER_ID}`);
  const grid = $('goalsGrid');

  if (!savedGoalsCache || !savedGoalsCache.length) {
    grid.innerHTML = '<div class="empty">No savings goals created yet. Click "+ Add Goal" to track your targets.</div>';
    return;
  }

  grid.innerHTML = savedGoalsCache.map(g => {
    const target = Number(g.target_amount) || 1;
    const saved = Number(g.saved_amount) || 0;
    const pct = Math.min(100, Math.round((saved / target) * 100));

    return `
      <div class="goal-card">
        <div>
          <div class="goal-card-header">
            <span class="account-type-badge">${pct >= 100 ? 'ACHIEVED' : `${pct}% REACHED`}</span>
            <span style="font-size:22px;">🎯</span>
          </div>
          <h3>${esc(g.goal_name)}</h3>
          <div style="font-size:13px; color:var(--text-secondary); margin-bottom:8px;">
            Target: <b>${money(target)}</b>
          </div>
          <div style="font-size:24px; font-weight:800; color:var(--primary); margin:6px 0;">
            ${money(saved)}
          </div>
          <div class="progress">
            <i class="${pct >= 100 ? 'safe' : 'safe'}" style="width:${pct}%;"></i>
          </div>
          <div style="font-size:12px; color:var(--text-secondary); margin-top:6px;">
            ${g.formatted_target_date ? `Target Date: ${esc(g.formatted_target_date)}` : 'No deadline specified'}
          </div>
        </div>

        <div class="card-actions">
          <button class="secondary btn-sm" onclick="editGoal(${g.goal_id})">Edit</button>
          <button class="danger-btn btn-sm" onclick="deleteGoal(${g.goal_id})">Delete</button>
        </div>
      </div>
    `;
  }).join('');
}

function openGoalModal(goal = null) {
  const isEdit = Boolean(goal);
  openModal(
    isEdit ? 'Edit Savings Goal' : 'Add Savings Goal',
    'Define milestone goals for investments, vehicles, holidays or emergency funds',
    `
      <label>Goal Name
        <input id="gName" value="${esc(goal?.goal_name || '')}" placeholder="e.g. Vacation Fund, New Laptop" required>
      </label>

      <label>Target Amount (${currency})
        <input id="gTarget" type="number" min="1" step="1" value="${goal?.target_amount || ''}" placeholder="50000" required>
      </label>

      <label>Already Saved (${currency})
        <input id="gCurrent" type="number" min="0" step="1" value="${goal?.saved_amount || 0}" placeholder="0">
      </label>

      <label>Target Date (Optional)
        <input id="gDate" type="date" value="${goal?.raw_target_date || ''}">
      </label>
    `,
    async () => {
      const name = $('gName').value.trim();
      const target = Number($('gTarget').value);
      const saved = Number($('gCurrent').value || 0);
      const date = $('gDate').value || null;

      if (!name || !target || target <= 0) {
        toast('Please enter a goal title and target amount.', 'warning');
        return;
      }

      const body = { user_id: USER_ID, goal_name: name, target_amount: target, saved_amount: saved, target_date: date };

      if (isEdit) {
        await api(`/api/goals/${goal.goal_id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Goal updated successfully.', 'success');
      } else {
        await api('/api/goals', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Goal created successfully.', 'success');
      }

      closeModal();
      await loadGoals();
    }
  );
}

function editGoal(id) {
  const g = savedGoalsCache.find(x => x.goal_id === id);
  if (g) openGoalModal(g);
}

function deleteGoal(id) {
  confirmDialog(
    'Delete Savings Goal',
    'Are you sure you want to remove this savings goal?',
    async () => {
      await api(`/api/goals/${id}?user_id=${USER_ID}`, { method: 'DELETE' });
      toast('Savings goal deleted.', 'success');
      await loadGoals();
    }
  );
}

// ============================================================
// BILLS & RECURRING PAYMENTS MODULE
// ============================================================
let billsCache = [];

async function loadBills() {
  billsCache = await api(`/api/bills?user_id=${USER_ID}`);
  const now = new Date();
  const nextWeek = new Date(Date.now() + 7 * 86400000);

  // Filter calculations
  const monthlyTotal = billsCache
    .filter(b => String(b.frequency).toLowerCase() === 'monthly')
    .reduce((s, b) => s + Number(b.amount), 0);

  const dueSoonTotal = billsCache
    .filter(b => {
      const d = new Date(b.due_date);
      return d >= now && d <= nextWeek;
    })
    .reduce((s, b) => s + Number(b.amount), 0);

  const subsTotal = billsCache
    .filter(b => String(b.bill_type).toLowerCase().includes('sub'))
    .reduce((s, b) => s + Number(b.amount), 0);

  $('billMonthly').textContent = money(monthlyTotal);
  $('billCount').textContent = `${billsCache.length} scheduled`;
  $('billDue').textContent = money(dueSoonTotal);
  $('billSubscriptions').textContent = money(subsTotal);

  const list = $('billsList');
  if (!billsCache || !billsCache.length) {
    list.innerHTML = '<div class="empty">No recurring payments configured. Click "+ Add Recurring Payment" to stay organized.</div>';
    return;
  }

  list.innerHTML = billsCache.map(b => `
    <div class="bill-item">
      <div class="bill-left">
        <div class="bill-icon">${b.bill_type === 'Subscription' ? '🔁' : '📄'}</div>
        <div class="bill-info">
          <b>${esc(b.bill_name)}</b>
          <span>${esc(b.frequency)} · Due: ${esc(b.formatted_due || b.due_date)} · Type: ${esc(b.bill_type)}</span>
        </div>
      </div>
      <div class="bill-right">
        <span class="bill-amount">${money(b.amount)}</span>
        <button class="secondary btn-sm" onclick="editBill(${b.bill_id})">Edit</button>
        <button class="danger-btn btn-sm" onclick="deleteBill(${b.bill_id})">Delete</button>
      </div>
    </div>
  `).join('');
}

function openBillModal(bill = null) {
  const isEdit = Boolean(bill);
  openModal(
    isEdit ? 'Edit Recurring Payment' : 'Add Recurring Payment',
    'Track utilities, rent, subscriptions, loan EMIs and insurance policies',
    `
      <label>Bill / Service Name
        <input id="blName" value="${esc(bill?.bill_name || '')}" placeholder="e.g. Netflix, Electricity Bill, Home Rent" required>
      </label>

      <label>Amount (${currency})
        <input id="blAmount" type="number" min="0.01" step="0.01" value="${bill?.amount || ''}" placeholder="0.00" required>
      </label>

      <label>Payment Due Date
        <input id="blDate" type="date" value="${bill?.raw_due_date || new Date().toISOString().slice(0, 10)}" required>
      </label>

      <label>Frequency
        <select id="blFreq">
          <option value="Monthly" ${bill?.frequency === 'Monthly' ? 'selected' : ''}>Monthly</option>
          <option value="Quarterly" ${bill?.frequency === 'Quarterly' ? 'selected' : ''}>Quarterly</option>
          <option value="Yearly" ${bill?.frequency === 'Yearly' ? 'selected' : ''}>Yearly</option>
          <option value="Weekly" ${bill?.frequency === 'Weekly' ? 'selected' : ''}>Weekly</option>
        </select>
      </label>

      <label>Payment Category
        <select id="blType">
          <option value="Bill" ${bill?.bill_type === 'Bill' ? 'selected' : ''}>Utility / Invoice Bill</option>
          <option value="Subscription" ${bill?.bill_type === 'Subscription' ? 'selected' : ''}>Subscription (SaaS, Streaming)</option>
        </select>
      </label>
    `,
    async () => {
      const name = $('blName').value.trim();
      const amount = Number($('blAmount').value);
      const date = $('blDate').value;
      const freq = $('blFreq').value;
      const type = $('blType').value;

      if (!name || !amount || amount <= 0 || !date) {
        toast('Please enter bill name, amount, and due date.', 'warning');
        return;
      }

      const body = { user_id: USER_ID, bill_name: name, amount, due_date: date, frequency: freq, bill_type: type, active: 1 };

      if (isEdit) {
        await api(`/api/bills/${bill.bill_id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Payment updated successfully.', 'success');
      } else {
        await api('/api/bills', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Recurring payment added.', 'success');
      }

      closeModal();
      await loadBills();
    }
  );
}

function editBill(id) {
  const b = billsCache.find(x => x.bill_id === id);
  if (b) openBillModal(b);
}

function deleteBill(id) {
  confirmDialog(
    'Delete Recurring Payment',
    'Are you sure you want to remove this recurring payment?',
    async () => {
      await api(`/api/bills/${id}?user_id=${USER_ID}`, { method: 'DELETE' });
      toast('Payment removed.', 'success');
      await loadBills();
    }
  );
}

// ============================================================
// INVESTMENTS MODULE
// ============================================================
let investmentsCache = [];

async function loadInvestments() {
  investmentsCache = await api(`/api/investments?user_id=${USER_ID}`);

  const totalInvested = investmentsCache.reduce((s, i) => s + Number(i.invested_amount), 0);
  const totalValue = investmentsCache.reduce((s, i) => s + Number(i.current_value), 0);
  const gain = totalValue - totalInvested;
  const roi = totalInvested > 0 ? ((gain / totalInvested) * 100).toFixed(1) : '0.0';

  $('investedTotal').textContent = money(totalInvested);
  $('investmentValue').textContent = money(totalValue);
  $('investmentGain').textContent = `${gain >= 0 ? '+' : ''}${money(gain)} (${gain >= 0 ? 'Profit' : 'Loss'})`;
  $('investmentReturn').textContent = `${roi}%`;
  $('investmentCount').textContent = `${investmentsCache.length} holdings`;

  const tbody = $('investmentsList');
  if (!investmentsCache || !investmentsCache.length) {
    tbody.innerHTML = '<tr><td colspan="6" class="empty">No investments recorded. Click "+ Add Investment" to begin portfolio tracking.</td></tr>';
    return;
  }

  tbody.innerHTML = investmentsCache.map(i => {
    const netGain = Number(i.current_value) - Number(i.invested_amount);
    const returnPct = Number(i.invested_amount) > 0 ? ((netGain / Number(i.invested_amount)) * 100).toFixed(1) : 0;

    return `
      <tr>
        <td><b>${esc(i.investment_name)}</b></td>
        <td><span class="badge badge-category">${esc(i.investment_type)}</span></td>
        <td>${money(i.invested_amount)}</td>
        <td><b>${money(i.current_value)}</b></td>
        <td class="${netGain >= 0 ? 'positive' : 'negative'}">
          ${netGain >= 0 ? '+' : ''}${money(netGain)} (${returnPct}%)
        </td>
        <td style="text-align:right;">
          <button class="secondary btn-sm" onclick="editInvestment(${i.investment_id})">Edit</button>
          <button class="danger-btn btn-sm" onclick="deleteInvestment(${i.investment_id})">Delete</button>
        </td>
      </tr>
    `;
  }).join('');
}

function openInvestmentModal(inv = null) {
  const isEdit = Boolean(inv);
  openModal(
    isEdit ? 'Edit Investment Holding' : 'Add Investment',
    'Track stocks, mutual funds, gold, fixed deposits or crypto',
    `
      <label>Investment Name / Ticker
        <input id="iName" value="${esc(inv?.investment_name || '')}" placeholder="e.g. S&P 500 ETF, Apple Inc, Gold Sovereign" required>
      </label>

      <label>Asset Class
        <select id="iType">
          <option value="Stocks" ${inv?.investment_type === 'Stocks' ? 'selected' : ''}>Stocks / Equities</option>
          <option value="Mutual Funds" ${inv?.investment_type === 'Mutual Funds' ? 'selected' : ''}>Mutual Funds / ETFs</option>
          <option value="Fixed Deposit" ${inv?.investment_type === 'Fixed Deposit' ? 'selected' : ''}>Fixed Deposit / Bonds</option>
          <option value="Real Estate" ${inv?.investment_type === 'Real Estate' ? 'selected' : ''}>Real Estate</option>
          <option value="Commodity" ${inv?.investment_type === 'Commodity' ? 'selected' : ''}>Gold / Silver / Commodities</option>
          <option value="Crypto" ${inv?.investment_type === 'Crypto' ? 'selected' : ''}>Cryptocurrency</option>
          <option value="Other" ${inv?.investment_type === 'Other' ? 'selected' : ''}>Other</option>
        </select>
      </label>

      <label>Total Invested Capital (${currency})
        <input id="iInvested" type="number" min="0" step="0.01" value="${inv?.invested_amount || ''}" placeholder="0.00" required>
      </label>

      <label>Current Market Value (${currency})
        <input id="iValue" type="number" min="0" step="0.01" value="${inv?.current_value || ''}" placeholder="0.00" required>
      </label>

      <label>Purchase Date (Optional)
        <input id="iDate" type="date" value="${inv?.raw_date || ''}">
      </label>
    `,
    async () => {
      const name = $('iName').value.trim();
      const type = $('iType').value;
      const invested = Number($('iInvested').value);
      const val = Number($('iValue').value);
      const date = $('iDate').value || null;

      if (!name || !Number.isFinite(invested) || !Number.isFinite(val)) {
        toast('Please enter investment name, invested amount and current value.', 'warning');
        return;
      }

      const body = { user_id: USER_ID, investment_name: name, investment_type: type, invested_amount: invested, current_value: val, investment_date: date };

      if (isEdit) {
        await api(`/api/investments/${inv.investment_id}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Investment updated successfully.', 'success');
      } else {
        await api('/api/investments', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(body)
        });
        toast('Investment added successfully.', 'success');
      }

      closeModal();
      await loadInvestments();
    }
  );
}

function editInvestment(id) {
  const inv = investmentsCache.find(x => x.investment_id === id);
  if (inv) openInvestmentModal(inv);
}

function deleteInvestment(id) {
  confirmDialog(
    'Delete Investment',
    'Are you sure you want to remove this investment holding?',
    async () => {
      await api(`/api/investments/${id}?user_id=${USER_ID}`, { method: 'DELETE' });
      toast('Investment removed.', 'success');
      await loadInvestments();
    }
  );
}

// ============================================================
// NET WORTH MODULE (ASSETS & LIABILITIES)
// ============================================================
async function loadNetworth() {
  const [nw, assetsList, liabilitiesList] = await Promise.all([
    api(`/api/networth?user_id=${USER_ID}`),
    api(`/api/assets?user_id=${USER_ID}`),
    api(`/api/liabilities?user_id=${USER_ID}`)
  ]);

  $('netWorth').textContent = money(nw.netWorth);
  $('assetsSummary').textContent = money(nw.assets);
  $('liabilitiesSummary').textContent = money(nw.liabilities);

  $('assetsTotal').textContent = money(nw.assets);
  $('liabilitiesTotal').textContent = money(nw.liabilities);

  $('nwBank').textContent = money(nw.bank);
  $('nwInvestments').textContent = money(nw.investments);
  $('nwOther').textContent = money(nw.otherAssets);

  // Custom physical assets
  const customAssetsContainer = $('assetsCustomList');
  if (assetsList.length > 0) {
    customAssetsContainer.innerHTML = assetsList.map(a => `
      <div class="asset-line">
        <span>${esc(a.asset_name)}</span>
        <div>
          <b>${money(a.amount)}</b>
          <button class="danger-btn btn-sm" style="margin-left:8px;" onclick="deleteSimple('assets', ${a.asset_id})">✕</button>
        </div>
      </div>
    `).join('');
  } else {
    customAssetsContainer.innerHTML = '';
  }

  // Liabilities list
  const liabContainer = $('liabilitiesList');
  if (liabilitiesList.length > 0) {
    liabContainer.innerHTML = liabilitiesList.map(l => `
      <div class="asset-line">
        <span>${esc(l.liability_name)}</span>
        <div>
          <b class="negative">${money(l.amount)}</b>
          <button class="danger-btn btn-sm" style="margin-left:8px;" onclick="deleteSimple('liabilities', ${l.liability_id})">✕</button>
        </div>
      </div>
    `).join('');
  } else {
    liabContainer.innerHTML = '<div class="empty">No liabilities recorded. Clean balance sheet!</div>';
  }
}

function openAssetModal() {
  openModal(
    'Add Property / Physical Asset',
    'Include vehicles, jewelry, land or tangible possessions',
    `
      <label>Asset Description
        <input id="sName" placeholder="e.g. Electric Vehicle, Gold Jewelry" required>
      </label>
      <label>Estimated Market Value (${currency})
        <input id="sAmount" type="number" min="0" step="1" placeholder="0.00" required>
      </label>
    `,
    async () => {
      const name = $('sName').value.trim();
      const amount = Number($('sAmount').value);
      if (!name || isNaN(amount)) {
        toast('Please enter asset name and valid value.', 'warning');
        return;
      }
      await api('/api/assets', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ user_id: USER_ID, asset_name: name, amount })
      });
      closeModal();
      toast('Asset added.', 'success');
      await loadNetworth();
    }
  );
}

function openLiabilityModal() {
  openModal(
    'Add Liability / Debt',
    'Record mortgages, personal loans, car loans or student debt',
    `
      <label>Liability Name
        <input id="sName" placeholder="e.g. Home Mortgage, Education Loan" required>
      </label>
      <label>Outstanding Amount (${currency})
        <input id="sAmount" type="number" min="0" step="1" placeholder="0.00" required>
      </label>
    `,
    async () => {
      const name = $('sName').value.trim();
      const amount = Number($('sAmount').value);
      if (!name || isNaN(amount)) {
        toast('Please enter liability title and amount.', 'warning');
        return;
      }
      await api('/api/liabilities', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ user_id: USER_ID, liability_name: name, amount })
      });
      closeModal();
      toast('Liability recorded.', 'success');
      await loadNetworth();
    }
  );
}

async function deleteSimple(type, id) {
  confirmDialog(
    `Delete ${type === 'assets' ? 'Asset' : 'Liability'}`,
    `Are you sure you want to delete this ${type === 'assets' ? 'asset' : 'liability'} record?`,
    async () => {
      await api(`/api/${type}/${id}?user_id=${USER_ID}`, { method: 'DELETE' });
      toast('Deleted successfully.', 'success');
      await loadNetworth();
    }
  );
}

// ============================================================
// REPORTS & INSIGHTS MODULE
// ============================================================
async function loadReports() {
  const data = await api(`/api/reports?user_id=${USER_ID}`);

  // Monthly Cash Flow
  const monthlyContainer = $('reportMonthly');
  if (data.monthly && data.monthly.length > 0) {
    monthlyContainer.innerHTML = data.monthly.map(m => {
      const net = Number(m.income) - Number(m.expenses);
      const rate = Number(m.income) > 0 ? Math.round((net / Number(m.income)) * 100) : 0;
      return `
        <div class="report-row">
          <div class="report-row-header">
            <b>${esc(m.month_name || m.month)}</b>
            <span class="${net >= 0 ? 'positive' : 'negative'}">
              Net: ${net >= 0 ? '+' : ''}${money(net)} (${rate}%)
            </span>
          </div>
          <div class="report-row-meta">
            <span>Income: ${money(m.income)}</span>
            <span>Expenses: ${money(m.expenses)}</span>
          </div>
          <div class="progress">
            <i class="${rate >= 20 ? 'safe' : rate > 0 ? 'warning' : 'danger'}" style="width:${Math.max(5, Math.min(100, rate))}%;"></i>
          </div>
        </div>
      `;
    }).join('');
  } else {
    monthlyContainer.innerHTML = '<div class="empty">No historical monthly data recorded.</div>';
  }

  // Categories Breakdown
  const catContainer = $('reportCategories');
  const catTotal = data.categories.reduce((s, c) => s + Number(c.total), 0);

  if (data.categories && data.categories.length > 0 && catTotal > 0) {
    catContainer.innerHTML = data.categories.map(c => {
      const pct = Math.round((Number(c.total) / catTotal) * 100);
      return `
        <div class="report-row">
          <div class="report-row-header">
            <span>
              <span class="badge-cat-dot" style="background:${c.color || 'var(--primary)'};"></span>
              ${c.icon ? c.icon + ' ' : ''}<b>${esc(c.category_name)}</b>
              <small style="color:var(--text-secondary); margin-left:6px;">(${c.tx_count} transactions)</small>
            </span>
            <b>${money(c.total)} (${pct}%)</b>
          </div>
          <div class="progress">
            <i style="background:${c.color || 'var(--primary)'}; width:${pct}%;"></i>
          </div>
        </div>
      `;
    }).join('');
  } else {
    catContainer.innerHTML = '<div class="empty">No expense categories recorded this month.</div>';
  }

  // Intelligent Financial Insights
  const insightsContainer = $('insights');
  const topExpense = data.categories[0];
  const insightsList = [];

  if (topExpense) {
    insightsList.push(`Your highest spending category this month is <b>${esc(topExpense.category_name)}</b> at <b>${money(topExpense.total)}</b>.`);
  }

  if (data.monthly && data.monthly.length > 0) {
    const avgExpense = Math.round(data.monthly.reduce((s, m) => s + Number(m.expenses), 0) / data.monthly.length);
    insightsList.push(`Your average monthly expenditure across the last ${data.monthly.length} months is <b>${money(avgExpense)}</b>.`);

    const highestIncomeMonth = [...data.monthly].sort((a, b) => Number(b.income) - Number(a.income))[0];
    if (highestIncomeMonth && Number(highestIncomeMonth.income) > 0) {
      insightsList.push(`Highest inflow was recorded in <b>${highestIncomeMonth.month_name}</b> at <b>${money(highestIncomeMonth.income)}</b>.`);
    }
  }

  if (!insightsList.length) {
    insightsList.push('Add transactions and category budgets to generate custom algorithmic financial insights.');
  }

  insightsContainer.innerHTML = insightsList.map(item => `
    <div class="insight-card">${item}</div>
  `).join('');
}

// ============================================================
// ASSISTANT CHAT MODULE
// ============================================================
async function askAssistant(promptText = '') {
  const input = $('assistantInput');
  const query = (promptText || input.value).trim();
  if (!query) return;

  const chat = $('chat');
  chat.insertAdjacentHTML('beforeend', `<div class="msg user">${esc(query)}</div>`);
  input.value = '';
  chat.scrollTop = chat.scrollHeight;

  try {
    const res = await api(`/api/assistant?user_id=${USER_ID}&q=${encodeURIComponent(query)}`);
    chat.insertAdjacentHTML('beforeend', `<div class="msg bot">${esc(res.answer)}</div>`);
  } catch (err) {
    chat.insertAdjacentHTML('beforeend', `<div class="msg bot" style="color:var(--negative);">Could not process query: ${esc(err.message)}</div>`);
  }
  chat.scrollTop = chat.scrollHeight;
}

// ============================================================
// MODAL SYSTEM
// ============================================================
function openModal(title, subtitle, bodyHtml, onSave) {
  $('modalTitle').textContent = title;
  $('modalSubtitle').textContent = subtitle || '';
  $('modalBody').innerHTML = bodyHtml;

  const modal = $('modal');
  modal.classList.add('show');

  const saveBtn = $('modalSave');
  saveBtn.textContent = 'Save';
  saveBtn.className = 'primary';
  saveBtn.onclick = async () => {
    try {
      saveBtn.disabled = true;
      saveBtn.textContent = 'Saving...';
      await onSave();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      saveBtn.disabled = false;
      saveBtn.textContent = 'Save';
    }
  };
}

function closeModal() {
  const modal = $('modal');
  if (modal) modal.classList.remove('show');
}

// Global modal backdrop close on Esc or outside click
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') closeModal();
});

window.onclick = e => {
  const modal = $('modal');
  if (e.target === modal) closeModal();
};

function focusSearch() {
  showPage('transactions').then(() => {
    setTimeout(() => {
      const search = $('searchTransactions');
      if (search) search.focus();
    }, 150);
  });
}

// ============================================================
// SYSTEM BOOTSTRAP
// ============================================================
async function boot() {
  try {
    navInit();

    // Check DB health and update indicator
    const connStatus = $('connectionStatus');
    try {
      const health = await api('/api/health');
      if (connStatus) {
        connStatus.className = 'connection-status connected';
        connStatus.innerHTML = `<span class="connection-status-dot"></span><span>MySQL Connected (${health.accounts} Accs, ${health.transactions} Txs)</span>`;
      }
    } catch (hErr) {
      if (connStatus) {
        connStatus.className = 'connection-status error';
        connStatus.innerHTML = '<span class="connection-status-dot"></span><span>Database Offline</span>';
      }
    }

    // Load users
    users = await api('/api/users');
    if (!users || !users.length) {
      toast('No user profiles found in database. Please create one.', 'warning');
      openUserModal();
      return;
    }

    // Set active user
    USER_ID = users[0].user_id;

    await setupUserSelector();
    await loadUser();
    await loadFormData();
    await showPage('dashboard');

  } catch (err) {
    console.error('Fatal initialization error:', err);
    const connStatus = $('connectionStatus');
    if (connStatus) {
      connStatus.className = 'connection-status error';
      connStatus.innerHTML = `<span class="connection-status-dot"></span><span>DB Error: ${esc(err.message)}</span>`;
    }
    toast(`Initialization error: ${err.message}`, 'error');
  }
}

// Start app
boot();
