-- Phase 2 种子数据：缺陷分类字典（标注工作台选择项）
INSERT INTO defect_categories (code, name, description, enabled, deleted) VALUES
    ('SCRATCH', '划痕', '表面线性划伤', 1, 0),
    ('DENT', '凹陷', '局部受压变形', 1, 0),
    ('CRACK', '裂纹', '材料开裂', 1, 0),
    ('STAIN', '污渍', '表面污染/油渍', 1, 0),
    ('DEFORM', '变形', '整体形状偏差', 1, 0),
    ('MISSING', '缺料', '材料缺失/缺口', 1, 0);
