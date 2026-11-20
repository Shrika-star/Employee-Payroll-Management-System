
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EmployeePayrollSystem {

    static final String USERNAME = "admin";
    static final String PASSWORD = "admin123";

    static Set<String> sessions = ConcurrentHashMap.newKeySet();

    static ArrayList<Employee> employees = new ArrayList<>();
    static ArrayList<CompanyLoan> loans = new ArrayList<>();
    static ArrayList<PayrollRecord> payrollRecords = new ArrayList<>();
    static ArrayList<String> activities = new ArrayList<>();

    // ================= EMPLOYEE CLASS =================

    static class Employee {
        String id, name, department;
        double basicSalary, bonus, deductionPercent;
        LocalDate joiningDate;

        Employee(String id, String name, String department,
                 double basicSalary, double bonus,
                 double deductionPercent) {

            this.id = id;
            this.name = name;
            this.department = department;
            this.basicSalary = basicSalary;
            this.bonus = bonus;
            this.deductionPercent = deductionPercent;
            this.joiningDate = LocalDate.now();
        }

        double grossSalary() {
            return basicSalary + bonus;
        }

        double deduction() {
            return grossSalary() * deductionPercent / 100;
        }

        double netSalary() {
            return grossSalary() - deduction();
        }
    }

    // ================= COMPANY LOAN CLASS =================

    static class CompanyLoan {
        String id, employeeId, employeeName, status;
        double amount, balance, emi;
        int months;

        CompanyLoan(String id, String employeeId,
                    String employeeName, double amount, int months) {

            this.id = id;
            this.employeeId = employeeId;
            this.employeeName = employeeName;
            this.amount = amount;
            this.months = months;
            this.balance = amount;
            this.emi = amount / months;
            this.status = "PENDING";
        }
    }

    // ================= PAYROLL CLASS =================

    static class PayrollRecord {
        String employeeId, employeeName, month;
        double gross, deduction, loanEMI, net;

        PayrollRecord(String employeeId, String employeeName,
                      String month, double gross, double deduction,
                      double loanEMI, double net) {

            this.employeeId = employeeId;
            this.employeeName = employeeName;
            this.month = month;
            this.gross = gross;
            this.deduction = deduction;
            this.loanEMI = loanEMI;
            this.net = net;
        }
    }

    // ================= MAIN METHOD =================

    public static void main(String[] args) throws IOException {

        addSampleData();

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8081), 0);

        server.createContext("/", EmployeePayrollSystem::handleRequest);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();

        System.out.println("Employee Payroll System Started!");
        System.out.println("Open http://localhost:8081");
        System.out.println("Username: admin");
        System.out.println("Password: admin123");
    }

    // ================= SAMPLE DATA =================

    static void addSampleData() {

        employees.add(new Employee("E101", "Arun",
                "Networking", 30000, 3000, 10));

        employees.add(new Employee("E102", "Priya",
                "Development", 35000, 2500, 10));

        employees.add(new Employee("E103", "Ravi",
                "IT Support", 28000, 2000, 10));

        employees.add(new Employee("E104", "Divya",
                "Database", 40000, 4000, 10));

        CompanyLoan loan = new CompanyLoan(
                "L101", "E102", "Priya", 12000, 12);

        loan.status = "APPROVED";
        loans.add(loan);

        activities.add("Employee Payroll System initialized.");
        activities.add("Sample employee records added.");
    }

    // ================= REQUEST HANDLER =================

    static void handleRequest(HttpExchange ex) throws IOException {

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        if (path.equals("/") && method.equals("GET")) {
            redirect(ex, "/login");
            return;
        }

        if (path.equals("/login") && method.equals("GET")) {
            send(ex, loginPage(""));
            return;
        }

        if (path.equals("/login") && method.equals("POST")) {

            Map<String, String> data = formData(ex);

            String username = data.getOrDefault("username", "");
            String password = data.getOrDefault("password", "");

            if (USERNAME.equals(username) &&
                    PASSWORD.equals(password)) {

                String sessionId = UUID.randomUUID().toString();
                sessions.add(sessionId);

                ex.getResponseHeaders().add(
                        "Set-Cookie",
                        "EPMS_SESSION=" + sessionId +
                        "; Path=/; HttpOnly; SameSite=Lax");

                redirect(ex, "/dashboard");

            } else {
                send(ex, loginPage("Invalid username or password!"));
            }

            return;
        }

        if (path.equals("/logout")) {

            String sessionId = getSessionId(ex);

            if (sessionId != null) {
                sessions.remove(sessionId);
            }

            ex.getResponseHeaders().add(
                    "Set-Cookie",
                    "EPMS_SESSION=; Path=/; Max-Age=0");

            redirect(ex, "/login");
            return;
        }

        String sessionId = getSessionId(ex);

        if (sessionId == null || !sessions.contains(sessionId)) {
            redirect(ex, "/login");
            return;
        }

        try {

            if (path.equals("/dashboard")) {
                send(ex, dashboardPage());
            }

            else if (path.equals("/employees")) {
                send(ex, employeePage());
            }

            else if (path.equals("/employees/add") &&
                    method.equals("POST")) {
                addEmployee(ex);
            }

            else if (path.equals("/employees/delete") &&
                    method.equals("POST")) {
                deleteEmployee(ex);
            }

            else if (path.equals("/salary")) {
                send(ex, salaryPage());
            }

            else if (path.equals("/loans")) {
                send(ex, loanPage());
            }

            else if (path.equals("/loans/apply") &&
                    method.equals("POST")) {
                applyLoan(ex);
            }

            else if (path.equals("/loans/action") &&
                    method.equals("POST")) {
                loanAction(ex);
            }

            else if (path.equals("/payroll")) {
                send(ex, payrollPage());
            }

            else if (path.equals("/payroll/process") &&
                    method.equals("POST")) {
                processPayroll(ex);
            }

            else if (path.equals("/reports")) {
                send(ex, reportPage());
            }

            else {
                send(ex, layout("Page Not Found",
                        "<div class='card'><h2>Page Not Found</h2></div>"));
            }

        } catch (Exception e) {
            e.printStackTrace();
            send(ex, layout("Error",
                    "<div class='card'><h2>Error</h2><p>" +
                    esc(e.getMessage()) + "</p></div>"));
        }
    }

    // ================= LOGIN PAGE =================

    static String loginPage(String error) {

        return """
        <!DOCTYPE html>
        <html>
        <head>
        <title>EPMS Login</title>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
        * { box-sizing:border-box; }
        body {
            margin:0;
            font-family:Arial;
            background:linear-gradient(135deg,#17243a,#416db4);
            height:100vh;
            display:flex;
            justify-content:center;
            align-items:center;
        }
        .login {
            background:white;
            width:380px;
            max-width:92%;
            padding:35px;
            border-radius:14px;
            box-shadow:0 10px 35px #0004;
        }
        h1,h2 { text-align:center;color:#315eaa; }
        label { display:block;margin:15px 0 7px; }
        input {
            width:100%;
            padding:12px;
            border:1px solid #ddd;
            border-radius:6px;
        }
        button {
            width:100%;
            padding:13px;
            margin-top:22px;
            background:#315eaa;
            color:white;
            border:0;
            border-radius:6px;
            cursor:pointer;
        }
        .error { color:red;text-align:center;margin-top:12px; }
        </style>
        </head>
        <body>
        <div class="login">
            <h1>EPMS</h1>
            <h2>Welcome Back!</h2>
            <p style="text-align:center">Employee Payroll Management System</p>

            <form method="post" action="/login">
                <label>Username</label>
                <input type="text" name="username" required>

                <label>Password</label>
                <input type="password" name="password" required>

                <button type="submit">LOGIN</button>
            </form>

            <div class="error">""" + esc(error) + """
            </div>
        </div>
        </body>
        </html>
        """;
    }

    // ================= DASHBOARD =================

    static String dashboardPage() {

        double gross = employees.stream()
                .mapToDouble(Employee::grossSalary).sum();

        double net = employees.stream()
                .mapToDouble(Employee::netSalary).sum();

        long pending = loans.stream()
                .filter(l -> l.status.equals("PENDING")).count();

        long approved = loans.stream()
                .filter(l -> l.status.equals("APPROVED")).count();

        double balance = loans.stream()
                .filter(l -> l.status.equals("APPROVED"))
                .mapToDouble(l -> l.balance).sum();

        StringBuilder b = new StringBuilder();

        b.append("""
        <div class='hero'>
            <h1>Welcome to your workspace!</h1>
            <p>Manage your organization efficiently.</p>
        </div>
        """);

        b.append("<div class='stats'>");

        b.append(statCard("Total Employees", employees.size()));
        b.append(statCard("Pending Requests", pending));
        b.append(statCard("Approved Loans", approved));
        b.append(statCard("Payroll Records", payrollRecords.size()));

        b.append("</div>");

        b.append("""
        <div class='grid'>
        <div class='card'>
        <h2>Organization Overview</h2>
        """);

        b.append(summaryRow("Total Employees", employees.size()));
        b.append(summaryRow("Total Gross Salary", money(gross)));
        b.append(summaryRow("Estimated Net Salary", money(net)));
        b.append(summaryRow("Outstanding Loan Balance", money(balance)));

        b.append("</div>");

        b.append("""
        <div class='card'>
        <h2>Quick Access</h2>
        <a class='quick' href='/employees'>Employee Information</a>
        <a class='quick' href='/salary'>Salary Overview</a>
        <a class='quick' href='/loans'>Loan Applications</a>
        <a class='quick' href='/payroll'>Monthly Processing</a>
        <a class='quick' href='/reports'>Financial Reports</a>
        </div>
        </div>
        """);

        b.append("<div class='card'><h2>Recent Activities</h2>");

        int count = 0;

        for (int i = activities.size() - 1;
             i >= 0 && count < 5; i--) {

            b.append("<p>• ")
             .append(esc(activities.get(i)))
             .append("</p>");

            count++;
        }

        b.append("</div>");

        return layout("Dashboard", b.toString());
    }

    // ================= EMPLOYEE PAGE =================

    static String employeePage() {

        StringBuilder b = new StringBuilder();

        b.append("""
        <div class='page-heading'>
        <h1>Employee Records</h1>
        <p>Register and manage employee information.</p>
        </div>

        <div class='card'>
        <h2>Add New Employee</h2>
        <form method='post' action='/employees/add'>
        <div class='form-grid'>
        """);

        b.append(input("Employee ID", "id", "E105", "text"));
        b.append(input("Employee Name", "name", "Enter name", "text"));
        b.append(input("Department", "department", "Enter department", "text"));
        b.append(input("Basic Salary", "salary", "Enter salary", "number"));
        b.append(input("Bonus", "bonus", "Enter bonus", "number"));
        b.append(input("Deduction Percentage", "deduction", "10", "number"));

        b.append("""
        </div>
        <button class='btn'>Add Employee</button>
        </form>
        </div>

        <div class='card'>
        <h2>Employee List</h2>
        <div class='table-wrap'>
        <table>
        <tr>
        <th>ID</th><th>Name</th><th>Department</th>
        <th>Basic Salary</th><th>Bonus</th><th>Action</th>
        </tr>
        """);

        for (Employee e : employees) {

            b.append("<tr><td>").append(esc(e.id))
             .append("</td><td>").append(esc(e.name))
             .append("</td><td>").append(esc(e.department))
             .append("</td><td>").append(money(e.basicSalary))
             .append("</td><td>").append(money(e.bonus))
             .append("</td><td>");

            b.append("<form method='post' action='/employees/delete'>")
             .append("<input type='hidden' name='id' value='")
             .append(esc(e.id)).append("'>")
             .append("<button class='btn danger'>Delete</button>")
             .append("</form></td></tr>");
        }

        b.append("</table></div></div>");

        return layout("Employees", b.toString());
    }

    static void addEmployee(HttpExchange ex) throws IOException {

        Map<String, String> d = formData(ex);

        String id = d.getOrDefault("id", "").trim();
        String name = d.getOrDefault("name", "").trim();
        String department = d.getOrDefault("department", "").trim();

        double salary = number(d.get("salary"));
        double bonus = number(d.get("bonus"));
        double deduction = number(d.get("deduction"));

        if (id.isEmpty() || name.isEmpty() || department.isEmpty()
                || salary <= 0 || bonus < 0
                || deduction < 0 || deduction > 100
                || findEmployee(id) != null) {

            redirect(ex, "/employees");
            return;
        }

        employees.add(new Employee(id, name, department,
                salary, bonus, deduction));

        activities.add("New employee " + name + " was added.");

        redirect(ex, "/employees");
    }

    static void deleteEmployee(HttpExchange ex) throws IOException {

        Map<String, String> d = formData(ex);

        Employee e = findEmployee(d.get("id"));

        if (e != null) {

            boolean hasLoan = loans.stream().anyMatch(l ->
                    l.employeeId.equals(e.id) &&
                    l.status.equals("APPROVED") &&
                    l.balance > 0);

            if (!hasLoan) {
                employees.remove(e);
                activities.add("Employee " + e.name + " was removed.");
            }
        }

        redirect(ex, "/employees");
    }

    // ================= SALARY PAGE =================

    static String salaryPage() {

        StringBuilder b = new StringBuilder();

        b.append("""
        <div class='page-heading'>
        <h1>Salary Details</h1>
        <p>View employee salary information.</p>
        </div>

        <div class='card'>
        <div class='table-wrap'>
        <table>
        <tr>
        <th>Employee ID</th><th>Name</th>
        <th>Basic Salary</th><th>Bonus</th>
        <th>Gross Salary</th><th>Deduction</th>
        <th>Net Salary</th>
        </tr>
        """);

        for (Employee e : employees) {

            b.append("<tr><td>").append(esc(e.id))
             .append("</td><td>").append(esc(e.name))
             .append("</td><td>").append(money(e.basicSalary))
             .append("</td><td>").append(money(e.bonus))
             .append("</td><td>").append(money(e.grossSalary()))
             .append("</td><td>").append(money(e.deduction()))
             .append("</td><td><b>").append(money(e.netSalary()))
             .append("</b></td></tr>");
        }

        b.append("</table></div></div>");

        return layout("Salary Details", b.toString());
    }

    // ================= COMPANY LOAN PAGE =================

    static String loanPage() {

        StringBuilder b = new StringBuilder();

        b.append("""
        <div class='page-heading'>
        <h1>Company Loan Management</h1>
        <p>Apply for and manage employee loans.</p>
        </div>

        <div class='card'>
        <h2>New Loan Application</h2>
        <form method='post' action='/loans/apply'>
        <div class='form-grid'>
        <div class='field'>
        <label>Employee</label>
        <select name='employeeId' required>
        """);

        for (Employee e : employees) {
            b.append("<option value='").append(esc(e.id))
             .append("'>").append(esc(e.id))
             .append(" - ").append(esc(e.name))
             .append("</option>");
        }

        b.append("""
        </select>
        </div>
        """);

        b.append(input("Loan Amount", "amount", "Enter amount", "number"));
        b.append(input("Repayment Months", "months", "12", "number"));

        b.append("""
        </div>
        <button class='btn'>Submit Application</button>
        </form>
        </div>

        <div class='card'>
        <h2>Loan Applications</h2>
        <div class='table-wrap'>
        <table>
        <tr>
        <th>Loan ID</th><th>Employee</th><th>Amount</th>
        <th>EMI</th><th>Balance</th><th>Status</th><th>Action</th>
        </tr>
        """);

        for (CompanyLoan l : loans) {

            b.append("<tr><td>").append(esc(l.id))
             .append("</td><td>").append(esc(l.employeeName))
             .append("</td><td>").append(money(l.amount))
             .append("</td><td>").append(money(l.emi))
             .append("</td><td>").append(money(l.balance))
             .append("</td><td><span class='status ")
             .append(l.status.toLowerCase())
             .append("'>").append(esc(l.status))
             .append("</span></td><td>");

            if (l.status.equals("PENDING")) {

                b.append("<form class='inline' method='post' action='/loans/action'>")
                 .append("<input type='hidden' name='id' value='")
                 .append(esc(l.id)).append("'>")
                 .append("<button class='btn' name='action' value='approve'>Approve</button>")
                 .append("<button class='btn danger' name='action' value='reject'>Reject</button>")
                 .append("</form>");

            } else {
                b.append("-");
            }

            b.append("</td></tr>");
        }

        b.append("</table></div></div>");

        return layout("Company Loans", b.toString());
    }

    static void applyLoan(HttpExchange ex) throws IOException {

        Map<String, String> d = formData(ex);

        Employee e = findEmployee(d.get("employeeId"));

        double amount = number(d.get("amount"));
        int months = (int) number(d.get("months"));

        if (e != null && amount > 0 && months > 0) {

            String id = "L" + (101 + loans.size());

            loans.add(new CompanyLoan(id, e.id,
                    e.name, amount, months));

            activities.add("Loan application submitted by " + e.name);
        }

        redirect(ex, "/loans");
    }

    static void loanAction(HttpExchange ex) throws IOException {

        Map<String, String> d = formData(ex);

        String id = d.get("id");
        String action = d.get("action");

        for (CompanyLoan l : loans) {

            if (l.id.equals(id) && l.status.equals("PENDING")) {

                if ("approve".equals(action)) {

                    l.status = "APPROVED";

                    activities.add("Loan approved for " + l.employeeName);

                } else if ("reject".equals(action)) {

                    l.status = "REJECTED";

                    activities.add("Loan rejected for " + l.employeeName);
                }

                break;
            }
        }

        redirect(ex, "/loans");
    }

    // ================= PAYROLL PAGE =================

    static String payrollPage() {

        StringBuilder b = new StringBuilder();

        b.append("""
        <div class='page-heading'>
        <h1>Payroll Processing</h1>
        <p>Calculate monthly salary and loan EMI.</p>
        </div>

        <div class='card'>
        <h2>Process Monthly Payroll</h2>
        <form method='post' action='/payroll/process'>
        <label>Payroll Month</label>
        <input type='month' name='month' value='""")
         .append(YearMonth.now())
         .append("""
        ' required>
        <button class='btn'>Process Payroll</button>
        </form>
        </div>

        <div class='card'>
        <h2>Employee Payroll Preview</h2>
        <div class='table-wrap'>
        <table>
        <tr>
        <th>Employee</th><th>Gross Salary</th>
        <th>Deduction</th><th>Loan EMI</th>
        <th>Estimated Net Salary</th>
        </tr>
        """);

        for (Employee e : employees) {

            double emi = monthlyEMI(e.id);
            double net = Math.max(0, e.netSalary() - emi);

            b.append("<tr><td>").append(esc(e.name))
             .append("</td><td>").append(money(e.grossSalary()))
             .append("</td><td>").append(money(e.deduction()))
             .append("</td><td>").append(money(emi))
             .append("</td><td><b>").append(money(net))
             .append("</b></td></tr>");
        }

        b.append("</table></div></div>");

        return layout("Payroll", b.toString());
    }

    static void processPayroll(HttpExchange ex) throws IOException {

        Map<String, String> d = formData(ex);

        String month = d.get("month");

        if (month == null) {
            redirect(ex, "/payroll");
            return;
        }

        YearMonth selectedMonth;

        try {
            selectedMonth = YearMonth.parse(month);
        } catch (Exception e) {
            redirect(ex, "/payroll");
            return;
        }

        for (Employee e : employees) {

            boolean alreadyProcessed = payrollRecords.stream()
                    .anyMatch(r -> r.employeeId.equals(e.id)
                            && r.month.equals(month));

            if (alreadyProcessed) {
                continue;
            }

            double gross = e.grossSalary();
            double deduction = e.deduction();
            double emi = 0;

            CompanyLoan loan = getApprovedLoan(e.id);

            if (loan != null && loan.balance > 0) {

                emi = Math.min(loan.emi, loan.balance);

                loan.balance -= emi;

                if (loan.balance < 0.01) {
                    loan.balance = 0;
                }
            }

            double net = Math.max(0, gross - deduction - emi);

            payrollRecords.add(new PayrollRecord(
                    e.id, e.name, month,
                    gross, deduction, emi, net));
        }

        activities.add("Payroll processed for " + selectedMonth);

        redirect(ex, "/reports");
    }

    // ================= REPORT PAGE =================

    static String reportPage() {

        StringBuilder b = new StringBuilder();

        double totalGross = payrollRecords.stream()
                .mapToDouble(r -> r.gross).sum();

        double totalNet = payrollRecords.stream()
                .mapToDouble(r -> r.net).sum();

        b.append("<div class='page-heading'><h1>Payroll Reports</h1>")
         .append("<p>View processed salary records.</p></div>");

        b.append("<div class='stats'>")
         .append(statCard("Payroll Records", payrollRecords.size()))
         .append(statCard("Gross Payroll", money(totalGross)))
         .append(statCard("Net Payroll", money(totalNet)))
         .append("</div>");

        b.append("""
        <div class='card'>
        <h2>Payroll History</h2>
        <div class='table-wrap'>
        <table>
        <tr>
        <th>Employee ID</th><th>Name</th><th>Month</th>
        <th>Gross</th><th>Deduction</th>
        <th>Loan EMI</th><th>Net Salary</th>
        </tr>
        """);

        for (int i = payrollRecords.size() - 1; i >= 0; i--) {

            PayrollRecord r = payrollRecords.get(i);

            b.append("<tr><td>").append(esc(r.employeeId))
             .append("</td><td>").append(esc(r.employeeName))
             .append("</td><td>").append(esc(r.month))
             .append("</td><td>").append(money(r.gross))
             .append("</td><td>").append(money(r.deduction))
             .append("</td><td>").append(money(r.loanEMI))
             .append("</td><td><b>").append(money(r.net))
             .append("</b></td></tr>");
        }

        if (payrollRecords.isEmpty()) {
            b.append("<tr><td colspan='7'>No payroll records available.</td></tr>");
        }

        b.append("</table></div></div>");

        return layout("Reports", b.toString());
    }

    // ================= COMMON METHODS =================

    static Employee findEmployee(String id) {

        if (id == null) return null;

        for (Employee e : employees) {
            if (e.id.equalsIgnoreCase(id)) {
                return e;
            }
        }

        return null;
    }

    static CompanyLoan getApprovedLoan(String employeeId) {

        for (CompanyLoan l : loans) {

            if (l.employeeId.equals(employeeId)
                    && l.status.equals("APPROVED")
                    && l.balance > 0) {

                return l;
            }
        }

        return null;
    }

    static double monthlyEMI(String employeeId) {

        CompanyLoan loan = getApprovedLoan(employeeId);

        if (loan == null) return 0;

        return Math.min(loan.emi, loan.balance);
    }

    static double number(String value) {

        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0;
        }
    }

    static String money(double amount) {
        return String.format(Locale.US, "₹%,.2f", amount);
    }

    static String esc(String value) {

        if (value == null) return "";

        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String input(String label, String name,
                        String placeholder, String type) {

        return "<div class='field'><label>" + label + "</label>"
                + "<input type='" + type + "' name='" + name
                + "' placeholder='" + placeholder
                + "' required></div>";
    }

    static String statCard(String title, Object value) {

        return "<div class='stat-card'><div class='stat-title'>"
                + title + "</div><div class='stat-value'>"
                + value + "</div></div>";
    }

    static String summaryRow(String title, Object value) {

        return "<div class='summary-row'><span>" + title
                + "</span><strong>" + value + "</strong></div>";
    }

    static Map<String, String> formData(HttpExchange ex)
            throws IOException {

        String text = new String(
                ex.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        Map<String, String> data = new HashMap<>();

        for (String pair : text.split("&")) {

            String[] parts = pair.split("=", 2);

            if (parts.length == 2) {

                data.put(
                        URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
        }

        return data;
    }

    static String getSessionId(HttpExchange ex) {

        List<String> cookies = ex.getRequestHeaders().get("Cookie");

        if (cookies == null) return null;

        for (String cookie : cookies) {

            for (String item : cookie.split(";")) {

                item = item.trim();

                if (item.startsWith("EPMS_SESSION=")) {
                    return item.substring("EPMS_SESSION=".length());
                }
            }
        }

        return null;
    }

    static void redirect(HttpExchange ex, String location)
            throws IOException {

        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }

    static void send(HttpExchange ex, String html)
            throws IOException {

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
                "Content-Type", "text/html; charset=UTF-8");

        ex.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ================= COMMON PAGE LAYOUT =================

    static String layout(String title, String body) {

        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>""" + esc(title) + """
         | Employee Payroll System</title>

        <style>
        * { box-sizing:border-box; }

        body {
            margin:0;
            font-family:Arial,sans-serif;
            background:#f4f6fb;
            color:#243047;
        }

        .sidebar {
            position:fixed;
            top:0;
            left:0;
            width:220px;
            height:100vh;
            background:#17243a;
            color:white;
            padding:25px 17px;
            overflow-y:auto;
        }

        .brand {
            font-size:24px;
            font-weight:bold;
            margin-bottom:8px;
        }

        .brand-sub {
            color:#aab6ca;
            font-size:12px;
            margin-bottom:30px;
        }

        .nav-title {
            color:#8998b1;
            font-size:11px;
            margin:20px 10px 10px;
        }

        .sidebar a {
            display:block;
            text-decoration:none;
            color:#d5deeb;
            padding:12px;
            margin:5px 0;
            border-radius:7px;
            font-size:14px;
        }

        .sidebar a:hover {
            background:#2d405f;
            color:white;
        }

        .main {
            margin-left:220px;
            padding:30px;
        }

        .topbar {
            display:flex;
            justify-content:space-between;
            align-items:center;
            margin-bottom:25px;
        }

        .topbar h2 { margin:0; }

        .admin {
            background:white;
            padding:10px 16px;
            border-radius:8px;
        }

        .hero {
            background:linear-gradient(120deg,#263f70,#476fbd);
            color:white;
            padding:30px;
            border-radius:12px;
            margin-bottom:22px;
        }

        .hero h1 { margin:0 0 10px; }

        .stats {
            display:grid;
            grid-template-columns:repeat(4,1fr);
            gap:15px;
            margin-bottom:22px;
        }

        .stat-card,.card {
            background:white;
            border-radius:11px;
            padding:21px;
            box-shadow:0 2px 10px #1e325010;
            margin-bottom:22px;
        }

        .stat-title {
            color:#64718a;
            font-size:13px;
        }

        .stat-value {
            font-size:23px;
            font-weight:bold;
            margin-top:10px;
            color:#243b63;
        }

        .grid {
            display:grid;
            grid-template-columns:1fr 1fr;
            gap:20px;
        }

        .card h2 {
            font-size:18px;
            margin-top:0;
        }

        .summary-row {
            display:flex;
            justify-content:space-between;
            padding:12px 0;
            border-bottom:1px solid #edf0f5;
            font-size:13px;
        }

        .summary-row strong { color:#263f70; }

        .quick {
            display:block;
            text-decoration:none;
            color:#315eaa;
            padding:12px;
            margin:8px 0;
            background:#f1f4fa;
            border-radius:7px;
        }

        .quick:hover { background:#e1e9f8; }

        .page-heading { margin-bottom:22px; }

        .page-heading p {
            color:#78859a;
            font-size:14px;
        }

        .form-grid {
            display:grid;
            grid-template-columns:repeat(2,1fr);
            gap:15px;
            margin-bottom:18px;
        }

        .field { margin-bottom:15px; }

        .field label,label {
            display:block;
            font-size:13px;
            margin-bottom:7px;
            color:#536078;
        }

        input,select {
            width:100%;
            padding:11px;
            border:1px solid #dce2ec;
            border-radius:6px;
            font-size:13px;
        }

        .btn {
            display:inline-block;
            border:0;
            background:#416db4;
            color:white;
            padding:10px 15px;
            border-radius:6px;
            cursor:pointer;
            text-decoration:none;
            font-size:12px;
            margin:3px;
        }

        .btn:hover { background:#2e568f; }

        .danger { background:#c65353; }

        .table-wrap {
            width:100%;
            overflow-x:auto;
        }

        table {
            width:100%;
            border-collapse:collapse;
            font-size:13px;
            min-width:650px;
        }

        th,td {
            padding:13px;
            border-bottom:1px solid #edf0f5;
            text-align:left;
        }

        th {
            color:#68758a;
            background:#f8f9fc;
        }

        .status {
            padding:5px 9px;
            border-radius:20px;
            font-size:11px;
        }

        .pending { background:#fff2d9;color:#986b1b; }
        .approved { background:#e4f6ed;color:#267b54; }
        .rejected { background:#fbe9e9;color:#a34444; }

        .inline {
            display:flex;
            gap:3px;
        }

        @media(max-width:950px) {
            .stats { grid-template-columns:repeat(2,1fr); }
            .grid { grid-template-columns:1fr; }
        }

        @media(max-width:650px) {
            .sidebar {
                position:relative;
                width:100%;
                height:auto;
            }

            .main {
                margin-left:0;
                padding:16px;
            }

            .stats,.form-grid {
                grid-template-columns:1fr;
            }
        }
        </style>
        </head>

        <body>

        <aside class="sidebar">
            <div class="brand">EPMS</div>
            <div class="brand-sub">Employee Payroll System</div>

            <div class="nav-title">MAIN MENU</div>
            <a href="/dashboard">Dashboard</a>
            <a href="/employees">Employees</a>
            <a href="/salary">Salary Details</a>
            <a href="/loans">Company Loans</a>
            <a href="/payroll">Payroll Processing</a>
            <a href="/reports">Reports</a>

            <div class="nav-title">ACCOUNT</div>
            <a href="/logout">Logout</a>
        </aside>

        <main class="main">

            <div class="topbar">
                <h2>""" + esc(title) + """
                </h2>
                <div class="admin">Administrator</div>
            </div>

            """ + body + """

        </main>
        </body>
        </html>
        """;
    }
}
