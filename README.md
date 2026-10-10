# DBMS DA3: Simple CRUD Web Application

## Architecture
- **Backend:** Plain Java using standard `HttpServer` (`BackendCRUD.java`). It exposes a simple REST API on port `8080`.
- **Frontend:** A standalone HTML page with embedded CSS/JS (`index.html`) that uses the browser's Fetch API to talk to the backend.
- **Database:** Oracle Database using standard JDBC (Oracle Thin Driver).

## Prerequisites
- Java Development Kit (JDK) installed (`javac` and `java` commands).
- The `ojdbc6.jar` file in the same directory (as mentioned in your lab instructions).
- Must be connected to the lab network (so the DB URL `172.16.9.72` is reachable).

## How to Run

1. **Compile the Backend**
   Open your terminal in this directory and run:
   ```bash
   javac -cp ".:ojdbc6.jar" BackendCRUD.java
   ```
   *(Note: If you are doing this on Windows instead of Linux, use a semicolon `;` instead of a colon `:` in the classpath: `-cp ".;ojdbc6.jar"`).*

2. **Start the Backend Server**
   ```bash
   java -cp ".:ojdbc6.jar" BackendCRUD
   ```
   You should see:
   `Connected to Oracle Database successfully!`
   `Table USERS_CRUD created successfully.`
   `Backend Server is running at http://localhost:8080`

3. **Open the Frontend**
   Simply double-click the `index.html` file in your file explorer, or open it in your web browser. 
   You can now use the UI to Create, Read, Update, and Delete users!
