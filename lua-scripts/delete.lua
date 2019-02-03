wrk.method = "DELETE"
local id = 0
request = function()
    local path = "/v0/entity?id=" .. id
    id = id + 1
    return wrk.format(nil, path)
end