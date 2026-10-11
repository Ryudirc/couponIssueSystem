-- KEYS: cache, lock / ARGV: UUID로 만든 lockToken, lockTtlMs
-- 반환: HIT | LOAD | WAIT (대기필요)

local cached = redis.call('GET',KEYS[1])
if cached then return {'HIT',cached} end

local acquired = redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2])
if acquired then return {'LOAD', ''} end
return {'WAIT', ''}
