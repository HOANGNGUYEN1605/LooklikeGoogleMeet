#!/bin/bash

echo "========================================"
echo "Setup Database for RTP Conference"
echo "========================================"
echo ""

# Kiểm tra MySQL có sẵn không
if ! command -v mysql &> /dev/null; then
    echo "ERROR: MySQL command not found!"
    echo "Please install MySQL and add it to PATH, or run the SQL script manually."
    echo ""
    echo "To run manually:"
    echo "  1. Open MySQL command line or MySQL Workbench"
    echo "  2. Run the SQL commands from: database/schema.sql"
    exit 1
fi

echo "MySQL found. Setting up database..."
echo ""

# Nhập MySQL root password
read -sp "Enter MySQL root password (press Enter if no password): " MYSQL_PASSWORD
echo ""

if [ -z "$MYSQL_PASSWORD" ]; then
    echo "Running schema.sql without password..."
    mysql -u root < schema.sql
else
    echo "Running schema.sql with password..."
    mysql -u root -p"$MYSQL_PASSWORD" < schema.sql
fi

if [ $? -eq 0 ]; then
    echo ""
    echo "========================================"
    echo "Database setup completed successfully!"
    echo "========================================"
    echo ""
    echo "Default user account:"
    echo "  Username: admin"
    echo "  Password: admin123"
    echo ""
else
    echo ""
    echo "========================================"
    echo "Database setup failed!"
    echo "========================================"
    echo ""
    echo "Please check:"
    echo "  1. MySQL is running"
    echo "  2. Root password is correct"
    echo "  3. You have permission to create database"
    echo ""
    echo "You can also run the SQL script manually:"
    echo "  mysql -u root -p < schema.sql"
    echo ""
    exit 1
fi




