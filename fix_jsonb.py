import os
import re

base_dir = r"d:\wq\AiPro\AiCustomerService\backend\aimacrodroid-server\src\main\java\com\aimacrodroid\domain\entity"

def process_file(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    # Check if file has @TableField(typeHandler = JacksonTypeHandler.class)
    if 'typeHandler = JacksonTypeHandler.class' not in content:
        return
        
    new_content = content
    
    # Add import if missing
    if 'import org.apache.ibatis.type.JdbcType;' not in new_content:
        # Find the last import
        last_import_idx = new_content.rfind('import ')
        if last_import_idx != -1:
            end_of_line = new_content.find('\n', last_import_idx)
            new_content = new_content[:end_of_line+1] + 'import org.apache.ibatis.type.JdbcType;\n' + new_content[end_of_line+1:]
        else:
            # Put after package
            pkg_end = new_content.find(';')
            new_content = new_content[:pkg_end+1] + '\n\nimport org.apache.ibatis.type.JdbcType;\n' + new_content[pkg_end+1:]

    # Replace all occurrences of typeHandler = JacksonTypeHandler.class
    # Be careful not to replace it if jdbcType = JdbcType.OTHER is already there
    new_content = re.sub(
        r'@TableField\(\s*(value\s*=\s*"[^"]*",\s*)?typeHandler\s*=\s*JacksonTypeHandler\.class\s*\)',
        lambda m: f'@TableField({m.group(1) or ""}typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER)',
        new_content
    )
    
    new_content = re.sub(
        r'@TableField\(\s*typeHandler\s*=\s*JacksonTypeHandler\.class\s*(,\s*value\s*=\s*"[^"]*")?\s*\)',
        lambda m: f'@TableField(typeHandler = JacksonTypeHandler.class, jdbcType = JdbcType.OTHER{m.group(1) or ""})',
        new_content
    )
        
    if new_content != content:
        print(f"Updated {filepath}")
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(new_content)

for root, dirs, files in os.walk(base_dir):
    for file in files:
        if file.endswith('.java'):
            process_file(os.path.join(root, file))

print("Done")
