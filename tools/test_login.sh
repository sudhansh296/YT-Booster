#!/bin/bash
echo "Testing SUB001 login..."
curl -s -X POST http://localhost:5000/subadmin/login \
  -H 'Content-Type: application/json' \
  -d '{"code":"SUB001","password":"sub001@pass"}'
echo ""
echo "Testing OWNER2026 login..."
curl -s -X POST http://localhost:5000/subadmin/login \
  -H 'Content-Type: application/json' \
  -d '{"code":"OWNER2026","password":"owner@2026"}'
echo ""
